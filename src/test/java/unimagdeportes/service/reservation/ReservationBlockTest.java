package unimagdeportes.service.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import unimagdeportes.dto.space.SpaceDtos.SpaceInfo;
import unimagdeportes.event.ReservationNotificationEvent;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.model.user.AppUser;
import unimagdeportes.repository.reservation.ReservationRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.block.BlockSchedule.Slot;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.user.UserService;

class ReservationBlockTest {
    private final ReservationRepository repository = mock(ReservationRepository.class);
    private final UserService users = mock(UserService.class);
    private final SpaceService spaces = mock(SpaceService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T13:00:00Z"), ZoneOffset.UTC);
    private final ReservationService service = new ReservationService(repository, mock(ReservationLock.class),
            clock, users, spaces, events, audit);
    private final UUID spaceId = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-11-11T14:00:00Z");
    private final Instant end = Instant.parse("2026-11-11T15:00:00Z");
    private final List<Slot> slots = List.of(new Slot(start, end));

    @BeforeEach
    void startSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void rejectsEntireOperationBeforeChangingFutureReservations() {
        var future = reservation(start, end, ReservationStatus.CONFIRMED);
        var inUse = reservation(clock.instant(), end, ReservationStatus.CONFIRMED);
        candidates(future, inUse);

        assertThatThrownBy(() -> service.cancelForBlock(spaceId, slots, "Evento", admin))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode()).isEqualTo("RESERVATION_IN_USE");
        verify(future, never()).cancelByAdministrator();
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void cancelsFutureReservationAndPublishesNotice() {
        var future = reservation(start, end, ReservationStatus.CONFIRMED);
        UUID userId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        when(future.getId()).thenReturn(reservationId);
        when(future.getUserId()).thenReturn(userId);
        var user = mock(AppUser.class);
        when(user.getEmail()).thenReturn("a@unimagdalena.edu.co");
        when(user.getName()).thenReturn("Ana");
        when(users.findById(userId)).thenReturn(user);
        var space = mock(SpaceInfo.class);
        when(space.name()).thenReturn("Cancha");
        when(spaces.get(spaceId)).thenReturn(space);
        candidates(future);

        assertThat(service.cancelForBlock(spaceId, slots, "Evento", admin)).isEqualTo(1);
        verify(future).cancelByAdministrator();
        verify(audit).record("RESERVATION_CANCELLED_BY_ADMIN", "RESERVATION", reservationId, admin);
        verify(events).publishEvent(new ReservationNotificationEvent(ReservationNotificationEvent.Type.BLOCK,
                reservationId, "a@unimagdalena.edu.co", "Ana", "Cancha", start, "Evento", null));
    }

    @Test
    void rechecksClockImmediatelyBeforeCancelling() {
        var future = reservation(start, end, ReservationStatus.CONFIRMED);
        candidates(future);
        var advancingClock = mock(Clock.class);
        when(advancingClock.instant()).thenReturn(clock.instant(), start);
        var timedService = new ReservationService(repository, mock(ReservationLock.class),
                advancingClock, users, spaces, events, audit);

        assertThatThrownBy(() -> timedService.cancelForBlock(spaceId, slots, "Evento", admin))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode()).isEqualTo("RESERVATION_IN_USE");
        verify(future, never()).cancelByAdministrator();
        var order = org.mockito.Mockito.inOrder(repository, advancingClock);
        order.verify(repository).findBySpaceIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                spaceId, List.of(ReservationStatus.CONFIRMED, ReservationStatus.IN_USE), end, start);
        order.verify(advancingClock, org.mockito.Mockito.times(2)).instant();
    }

    @Test
    void rejectsBeforeCommitWhenReservationBeginsDuringTransaction() {
        var future = reservation(start, end, ReservationStatus.CONFIRMED);
        UUID userId = UUID.randomUUID();
        when(future.getId()).thenReturn(UUID.randomUUID());
        when(future.getUserId()).thenReturn(userId);
        var user = mock(AppUser.class);
        when(user.getEmail()).thenReturn("a@unimagdalena.edu.co");
        when(user.getName()).thenReturn("Ana");
        when(users.findById(userId)).thenReturn(user);
        var space = mock(SpaceInfo.class);
        when(space.name()).thenReturn("Cancha");
        when(spaces.get(spaceId)).thenReturn(space);
        candidates(future);
        var advancingClock = mock(Clock.class);
        when(advancingClock.instant()).thenReturn(clock.instant(), clock.instant(), start);
        var timedService = new ReservationService(repository, mock(ReservationLock.class),
                advancingClock, users, spaces, events, audit);

        timedService.cancelForBlock(spaceId, slots, "Evento", admin);

        assertThatThrownBy(() -> TransactionSynchronizationManager.getSynchronizations().getFirst()
                .beforeCommit(false)).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode()).isEqualTo("RESERVATION_IN_USE");
    }

    private Reservation reservation(Instant from, Instant until, ReservationStatus status) {
        var reservation = mock(Reservation.class);
        when(reservation.getStartAt()).thenReturn(from);
        when(reservation.getEndAt()).thenReturn(until);
        when(reservation.getStatus()).thenReturn(status);
        return reservation;
    }

    private void candidates(Reservation... reservations) {
        when(repository.findBySpaceIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                spaceId, List.of(ReservationStatus.CONFIRMED, ReservationStatus.IN_USE), end, start))
                .thenReturn(List.of(reservations));
    }
}
