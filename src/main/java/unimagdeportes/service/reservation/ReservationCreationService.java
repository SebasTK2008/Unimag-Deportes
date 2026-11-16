package unimagdeportes.service.reservation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.config.UsageRuleSettings;
import unimagdeportes.dto.mail.Receipt;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.event.ReservationCreatedEvent;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.model.space.SpaceStatus;
import unimagdeportes.repository.reservation.ReservationRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.availability.AvailabilityService;
import unimagdeportes.service.equipment.EquipmentService;
import unimagdeportes.service.equipment.EquipmentService.RequestedEquipment;
import unimagdeportes.service.equipment.EquipmentService.ReservedEquipment;
import unimagdeportes.service.parameter.ParameterService;
import unimagdeportes.service.policy.BookingWindowPolicy;
import unimagdeportes.service.policy.QuotaPolicy;
import unimagdeportes.service.policy.WeekCalculator;
import unimagdeportes.service.quota.QuotaService;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.user.UserService;

@Service
public class ReservationCreationService {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final ReservationRepository reservations;
    private final ReservationLock lock;
    private final UserService users;
    private final SpaceService spaces;
    private final AvailabilityService availability;
    private final EquipmentService equipment;
    private final ParameterService parameters;
    private final QuotaService quota;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final UsageRuleSettings settings;
    private final Clock clock;
    private final String contactUrl;
    private final BookingWindowPolicy window = new BookingWindowPolicy();
    private final QuotaPolicy quotaPolicy = new QuotaPolicy();
    private final WeekCalculator weeks = new WeekCalculator();

    public ReservationCreationService(ReservationRepository reservations, ReservationLock lock, UserService users,
            SpaceService spaces, AvailabilityService availability, EquipmentService equipment, ParameterService parameters, QuotaService quota,
            AuditService audit, ApplicationEventPublisher events, UsageRuleSettings settings, Clock clock,
            @Value("${app.mail.contact-url:}") String contactUrl) {
        this.reservations = reservations;
        this.lock = lock;
        this.users = users;
        this.spaces = spaces;
        this.availability = availability;
        this.equipment = equipment;
        this.parameters = parameters;
        this.quota = quota;
        this.audit = audit;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
        this.contactUrl = contactUrl;
    }

    public record Created(UUID id, UUID spaceId, String spaceName, Instant startAt, Instant endAt,
            ReservationStatus status, Instant createdAt, List<ReservedEquipment> equipment) { }

    @Transactional
    public Created create(UUID userId, UUID spaceId, Instant start, int slots, List<RequestedEquipment> requested) {
        lock.acquire();
        var user = users.findForUpdate(userId);
        var space = spaces.getForUpdate(spaceId);
        Instant now = clock.instant();
        if (user.getSuspendedUntil() != null && user.getSuspendedUntil().isAfter(now)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SUSPENDED",
                    "Estás suspendido y no puedes crear reservas.",
                    Map.of("suspendedUntil", user.getSuspendedUntil().atZone(ZONE).toOffsetDateTime()));
        }
        if (user.getName() == null || user.getName().isBlank()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PROFILE_INCOMPLETE", "Completa tu perfil.");
        }
        if (user.getEmail() == null || !user.getEmail().matches("^[a-z0-9._%+-]+@unimagdalena\\.edu\\.co$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_DOMAIN", "Correo institucional inválido.");
        }
        if (slots < 1 || slots > 2 || start == null || !start.atZone(ZONE).toLocalTime()
                .equals(start.atZone(ZONE).toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.HOURS))) {
            String field = slots < 1 || slots > 2 ? "slots" : "startAt";
            String reason = field.equals("slots") ? "debe ser 1 o 2" : "debe estar alineado a una hora exacta";
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La reserva debe tener una o dos franjas consecutivas alineadas a horas exactas.",
                    Map.of("fields", Map.of(field, reason)));
        }
        Instant end = start.plus(Duration.ofHours(slots));
        var localStart = start.atZone(ZONE);
        var localEnd = end.atZone(ZONE);
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())
                || localStart.toLocalTime().isBefore(space.openingTime())
                || localEnd.toLocalTime().isAfter(space.closingTime())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Las franjas deben estar dentro del horario del espacio.",
                    Map.of("fields", Map.of("startAt", "las franjas deben estar dentro del horario del espacio")));
        }
        if (!window.isBookable(now, start, Duration.ofMinutes(settings.bookingToleranceMinutes()),
                Duration.ofMinutes(settings.minAdvanceMinutes()))) {
            throw new BusinessException(HttpStatus.CONFLICT, "OUTSIDE_BOOKING_WINDOW",
                    "La franja está fuera de la ventana de reserva.");
        }
        if (space.status() != SpaceStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "SPACE_IN_MAINTENANCE",
                    "El espacio está en mantenimiento.");
        }
        var occupancy = availability.overlapping(spaceId, start, end);
        if (!occupancy.blocked().isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "BLOCKED_SLOT", "La franja tiene un bloqueo institucional.");
        }
        var occupying = Arrays.stream(ReservationStatus.values()).filter(ReservationStatus::occupiesSpace).toList();
        if (!occupancy.occupied().isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "SLOT_TAKEN", "La franja ya no está disponible.");
        }
        if (!reservations.findByUserIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                userId, occupying, end, start).isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "OVERLAPPING_RESERVATION",
                    "Ya tienes una reserva que se solapa.");
        }
        checkQuota(userId, start, end);
        var reserved = equipment.validateForReservation(requested, start, end);
        var reservation = new Reservation(UUID.randomUUID(), userId, spaceId, start, end, now,
                settings.lateCancellationMinutes(), settings.checkinOpeningMinutes());
        reservations.saveAndFlush(reservation);
        equipment.attach(reservation.getId(), reserved);
        audit.record("RESERVATION_CREATED", "RESERVATION", reservation.getId(), userId);
        events.publishEvent(new AvailabilityChangedEvent(spaceId));
        events.publishEvent(new ReservationCreatedEvent(new Receipt(user.getEmail(), user.getName(), space.name(),
                start, end, reserved.stream().map(item -> new Receipt.EquipmentItem(item.name(), item.quantity())).toList(),
                "Cancelación tardía con menos de " + settings.lateCancellationMinutes()
                + " minutos de anticipación.", contactUrl)));
        return new Created(reservation.getId(), spaceId, space.name(), start, end, reservation.getStatus(), now, reserved);
    }

    private void checkQuota(UUID userId, Instant start, Instant end) {
        var week = weeks.startOfWeek(start.atZone(ZONE).toLocalDate());
        Duration limit = Duration.ofHours(parameters.current().weeklyQuotaHours());
        while (week.toInstant().isBefore(end)) {
            Instant weekStart = week.toInstant();
            Instant weekEnd = week.plusWeeks(1).toInstant();
            Duration used = quota.usedInWeek(userId, weekStart, weekEnd);
            Duration requested = quotaPolicy.usedInWeek(List.of(new QuotaPolicy.Interval(start, end)), weekStart, weekEnd);
            Duration available = quotaPolicy.available(limit, used);
            if (requested.compareTo(available) > 0) {
                throw new BusinessException(HttpStatus.CONFLICT, "QUOTA_EXCEEDED",
                        "La reserva supera tu cuota semanal. Horas disponibles: " + hours(available) + ".",
                        Map.of("weekStart", week.toOffsetDateTime(), "weeklyLimitHours", hours(limit),
                                "usedHours", hours(used), "requestedHours", hours(requested),
                                "availableHours", hours(available)));
            }
            week = week.plusWeeks(1);
        }
    }

    private double hours(Duration duration) {
        return duration.toSeconds() / 3600.0;
    }
}
