package unimagdeportes.service.reservation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.dto.reservation.ReservationDtos;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.event.ReservationNotificationEvent;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mapper.reservation.ReservationMapper;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.repository.reservation.ReservationRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.equipment.EquipmentService;
import unimagdeportes.service.policy.CancellationPolicy;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.user.UserService;

/**
 * Cancelación de una reserva propia: vista previa del efecto en la cuota y
 * cancelación a tiempo o tardía (RN-20 a RN-26). La cancelación libera el espacio,
 * conserva los implementos como historial y notifica por correo tras el commit.
 */
@Service
public class CancellationService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final ReservationRepository reservations;
    private final ReservationLock lock;
    private final Clock clock;
    private final UserService users;
    private final SpaceService spaces;
    private final EquipmentService equipment;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ReservationMapper mapper;
    private final CancellationPolicy policy = new CancellationPolicy();

    public CancellationService(ReservationRepository reservations, ReservationLock lock, Clock clock,
            UserService users, SpaceService spaces, EquipmentService equipment, AuditService audit,
            ApplicationEventPublisher events, ReservationMapper mapper) {
        this.reservations = reservations;
        this.lock = lock;
        this.clock = clock;
        this.users = users;
        this.spaces = spaces;
        this.equipment = equipment;
        this.audit = audit;
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ReservationDtos.CancellationPreview preview(UUID userId, UUID reservationId) {
        var reservation = ownedReservation(userId, reservationId);
        requireCancellable(reservation);
        boolean late = policy.isLate(clock.instant(), reservation.getStartAt(),
                reservation.getCancellationThresholdMinutes());
        int hours = policy.hoursCounted(reservation.getStartAt(), reservation.getEndAt(), late);
        return new ReservationDtos.CancellationPreview(reservationId, late, hours, message(late));
    }

    @Transactional
    public ReservationDtos.CancellationResult cancel(UUID userId, UUID reservationId) {
        lock.acquire();
        var reservation = ownedReservation(userId, reservationId);
        requireCancellable(reservation);
        Instant now = clock.instant();
        boolean late = policy.isLate(now, reservation.getStartAt(), reservation.getCancellationThresholdMinutes());
        int hours = policy.hoursCounted(reservation.getStartAt(), reservation.getEndAt(), late);
        var space = spaces.get(reservation.getSpaceId());
        if (late) {
            reservation.cancelLate();
        } else {
            reservation.cancelOnTime();
        }
        audit.record("RESERVATION_CANCELLED", "RESERVATION", reservation.getId(), userId);
        var user = users.findById(userId);
        events.publishEvent(new ReservationNotificationEvent(ReservationNotificationEvent.Type.CANCELLATION,
                reservation.getId(), user.getEmail(), user.getName(), space.name(),
                reservation.getStartAt(), null, null));
        events.publishEvent(new AvailabilityChangedEvent(reservation.getSpaceId()));
        return new ReservationDtos.CancellationResult(reservation.getId(), reservation.getStatus(),
                now.atZone(BOGOTA).toOffsetDateTime(), hours,
                equipment.forReservation(reservation.getId()).stream().map(mapper::toEquipment).toList());
    }

    private Reservation ownedReservation(UUID userId, UUID reservationId) {
        var reservation = reservations.findById(reservationId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "La reserva no existe."));
        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "La reserva pertenece a otro usuario.");
        }
        return reservation;
    }

    private void requireCancellable(Reservation reservation) {
        boolean checkedIn = reservation.getCheckedInAt() != null;
        if (checkedIn || reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELLABLE",
                    checkedIn ? "La reserva ya tiene check-in y no se puede cancelar."
                              : "La reserva no se puede cancelar.");
        }
    }

    private String message(boolean late) {
        return late
                ? "La cancelación es tardía: el espacio queda libre, pero las horas siguen contando como usadas."
                : "La cancelación es a tiempo: el espacio queda libre y las horas se devuelven a tu cuota.";
    }
}
