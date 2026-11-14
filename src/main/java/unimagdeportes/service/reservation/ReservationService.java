package unimagdeportes.service.reservation;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.repository.reservation.ReservationRepository;
import unimagdeportes.event.ReservationNotificationEvent;
import unimagdeportes.event.ReservationNotificationEvent.Type;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.user.UserService;

@Service
public class ReservationService {
    private final ReservationRepository reservations;
    private final ReservationLock lock;
    private final Clock clock;
    private final UserService users;
    private final SpaceService spaces;
    private final ApplicationEventPublisher events;

    public ReservationService(ReservationRepository reservations, ReservationLock lock, Clock clock,
            UserService users, SpaceService spaces, ApplicationEventPublisher events) {
        this.reservations = reservations;
        this.lock = lock;
        this.clock = clock;
        this.users = users;
        this.spaces = spaces;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public ReservationInfo get(UUID id) {
        var reservation = reservations.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "La reserva no existe."));
        return new ReservationInfo(reservation.getId(), reservation.getUserId(), reservation.getSpaceId(),
                reservation.getStartAt(), reservation.getEndAt(), reservation.getStatus());
    }

    public record ReservationInfo(UUID id, UUID userId, UUID spaceId, Instant startAt, Instant endAt,
            ReservationStatus status) { }

    @Transactional(readOnly = true)
    public List<UUID> dueReminders() {
        return reservations.findDueReminders(clock.instant());
    }

    @Transactional
    public void requestReminder(UUID id) {
        lock.acquire();
        var reservation = reservations.findById(id).orElse(null);
        Instant now = clock.instant();
        if (reservation == null || reservation.getStatus() != ReservationStatus.CONFIRMED
                || reservation.getReminderSentAt() != null
                || reservation.getCreatedAt().isAfter(reservation.getStartAt().minus(Duration.ofHours(2)))
                || now.isBefore(reservation.getStartAt().minus(Duration.ofHours(2)))
                || !now.isBefore(reservation.getStartAt())) return;
        var user = users.findById(reservation.getUserId());
        var space = spaces.get(reservation.getSpaceId());
        events.publishEvent(new ReservationNotificationEvent(Type.REMINDER, id, user.getEmail(), user.getName(),
                space.name(), reservation.getStartAt(), null, null));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markReminderSent(UUID id) {
        reservations.findById(id).ifPresent(reservation -> reservation.markReminderSent(clock.instant()));
    }
}
