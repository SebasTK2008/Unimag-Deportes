package unimagdeportes.service.sanction;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.config.UsageRuleSettings;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.event.ReservationNotificationEvent;
import unimagdeportes.event.ReservationNotificationEvent.Type;
import unimagdeportes.model.user.AppUser;
import unimagdeportes.repository.sanction.MonthlySuspensionRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.parameter.ParameterService;
import unimagdeportes.service.policy.SanctionPolicy;
import unimagdeportes.service.reservation.ReservationLock;
import unimagdeportes.service.reservation.ReservationService;
import unimagdeportes.service.reservation.ReservationService.CheckInCandidate;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.user.UserService;

/**
 * Sanciones automáticas (UC10): registra la inasistencia de una reserva sin check-in al
 * cerrar su ventana y, si se alcanza el umbral del mes, suspende al usuario y cancela sus
 * reservas futuras contenidas en la suspensión (RN-37 a RN-40). Todo ocurre en una sola
 * transacción; los correos salen después del commit (D-10). La auditoría no tiene actor
 * porque las acciones las realiza el sistema.
 */
@Service
public class SanctionService {
    private static final String SUSPENSION_REASON = "suspensión de tu cuenta por inasistencias";
    private final ReservationService reservations;
    private final MonthlySuspensionRepository monthlySuspensions;
    private final ReservationLock lock;
    private final Clock clock;
    private final UserService users;
    private final SpaceService spaces;
    private final ParameterService parameters;
    private final UsageRuleSettings settings;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final SanctionPolicy policy = new SanctionPolicy();

    public SanctionService(ReservationService reservations, MonthlySuspensionRepository monthlySuspensions,
            ReservationLock lock, Clock clock, UserService users, SpaceService spaces, ParameterService parameters,
            UsageRuleSettings settings, AuditService audit, ApplicationEventPublisher events) {
        this.reservations = reservations;
        this.monthlySuspensions = monthlySuspensions;
        this.lock = lock;
        this.clock = clock;
        this.users = users;
        this.spaces = spaces;
        this.parameters = parameters;
        this.settings = settings;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<UUID> closedWithoutCheckIn() {
        return reservations.closedWithoutCheckIn();
    }

    /** Idempotente: tras el bloqueo relee la reserva y no hace nada si ya no corresponde marcarla. */
    @Transactional
    public void markNoShow(UUID reservationId) {
        lock.acquire();
        Instant now = clock.instant();
        var reservation = reservations.noShowCandidate(reservationId, now).orElse(null);
        if (reservation == null) return;
        // Orden global de bloqueo de filas (plan general §4.1): primero el usuario.
        var user = users.findForUpdate(reservation.userId());
        reservations.registerNoShow(reservation.id());
        audit.record("NO_SHOW_REGISTERED", "RESERVATION", reservation.id(), null);
        var spaceName = spaces.get(reservation.spaceId()).name();
        events.publishEvent(new ReservationNotificationEvent(Type.NO_SHOW, reservation.id(), user.getEmail(),
                user.getName(), spaceName, reservation.startAt(), null, null));
        evaluateSuspension(user, reservation, spaceName, now);
    }

    private void evaluateSuspension(AppUser user, CheckInCandidate noShow, String spaceName, Instant now) {
        var month = policy.absenceMonth(noShow.endAt());
        var range = policy.range(month);
        long noShows = reservations.noShowsInPeriod(user.getId(), range.start(), range.end());
        Instant until = policy.suspensionEnd(now, settings.suspensionDays(), user.getSuspendedUntil());
        if (!policy.reachesThreshold(noShows, parameters.current().noShowThreshold())
                || !monthlySuspensions.register(user.getId(), month, now, until)) {
            return;
        }
        user.suspendUntil(until);
        audit.recordSuspension(user.getId(), until);
        events.publishEvent(new ReservationNotificationEvent(Type.SUSPENSION, noShow.id(), user.getEmail(),
                user.getName(), spaceName, noShow.startAt(), null, until));
        cancelReservationsDuringSuspension(user, now, until);
    }

    /** Solo las contenidas por completo en la suspensión; las parcialmente solapadas se conservan (RN-40). */
    private void cancelReservationsDuringSuspension(AppUser user, Instant now, Instant until) {
        var affected = reservations.cancelDuringSuspension(user.getId(), now, until);
        for (var reservation : affected) {
            audit.record("RESERVATION_CANCELLED_BY_SUSPENSION", "RESERVATION", reservation.id(), null);
            events.publishEvent(new ReservationNotificationEvent(Type.CANCELLATION, reservation.id(),
                    user.getEmail(), user.getName(), spaces.get(reservation.spaceId()).name(),
                    reservation.startAt(), SUSPENSION_REASON, null));
            events.publishEvent(new AvailabilityChangedEvent(reservation.spaceId()));
        }
    }
}
