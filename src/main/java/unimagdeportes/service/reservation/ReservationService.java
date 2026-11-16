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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.repository.reservation.ReservationRepository;
import unimagdeportes.event.ReservationNotificationEvent;
import unimagdeportes.event.ReservationNotificationEvent.Type;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.user.UserService;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.block.BlockSchedule.Slot;

@Service
public class ReservationService {
    private final ReservationRepository reservations;
    private final ReservationLock lock;
    private final Clock clock;
    private final UserService users;
    private final SpaceService spaces;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public ReservationService(ReservationRepository reservations, ReservationLock lock, Clock clock,
            UserService users, SpaceService spaces, ApplicationEventPublisher events, AuditService audit) {
        this.reservations = reservations;
        this.lock = lock;
        this.clock = clock;
        this.users = users;
        this.spaces = spaces;
        this.events = events;
        this.audit = audit;
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

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<Reservation> checkInCandidates(UUID spaceId, UUID userId) {
        return reservations.findBySpaceIdAndUserIdOrderByStartAtAsc(spaceId, userId);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean hasOtherUserInProgress(UUID spaceId, UUID userId, Instant now) {
        return reservations.existsBySpaceIdAndUserIdNotAndStatusInAndStartAtLessThanEqualAndEndAtGreaterThan(
                spaceId, userId, List.of(ReservationStatus.CONFIRMED, ReservationStatus.IN_USE), now, now);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public int cancelForBlock(UUID spaceId, List<Slot> slots, String reason, UUID administratorId) {
        Instant first = slots.stream().map(Slot::start).min(Instant::compareTo).orElseThrow();
        Instant last = slots.stream().map(Slot::end).max(Instant::compareTo).orElseThrow();
        var candidates = reservations.findBySpaceIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(spaceId,
                List.of(ReservationStatus.CONFIRMED, ReservationStatus.IN_USE), last, first);
        Instant now = clock.instant();
        var affected = candidates.stream()
                .filter(reservation -> reservation.getEndAt().isAfter(now))
                .filter(reservation -> overlaps(slots, reservation.getStartAt(), reservation.getEndAt())).toList();
        affected.forEach(reservation -> requireFuture(reservation, now));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                Instant commitTime = clock.instant();
                affected.forEach(reservation -> requireFuture(reservation, commitTime));
            }
        });
        var space = spaces.get(spaceId);
        for (var reservation : affected) {
            requireFuture(reservation, clock.instant());
            reservation.cancelByAdministrator();
            audit.record("RESERVATION_CANCELLED_BY_ADMIN", "RESERVATION", reservation.getId(), administratorId);
            var user = users.findById(reservation.getUserId());
            events.publishEvent(new ReservationNotificationEvent(Type.BLOCK, reservation.getId(), user.getEmail(),
                    user.getName(), space.name(), reservation.getStartAt(), reason, null));
        }
        return affected.size();
    }

    private void requireFuture(unimagdeportes.model.reservation.Reservation reservation, Instant now) {
        if (reservation.getStatus() == ReservationStatus.IN_USE || !reservation.getStartAt().isAfter(now)) {
            throw new BusinessException(HttpStatus.CONFLICT, "RESERVATION_IN_USE",
                    "El bloqueo afecta una reserva en curso.");
        }
    }

    private boolean overlaps(List<Slot> slots, Instant start, Instant end) {
        int low = 0;
        int high = slots.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (!slots.get(middle).end().isAfter(start)) low = middle + 1;
            else high = middle;
        }
        return low < slots.size() && slots.get(low).start().isBefore(end);
    }

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
