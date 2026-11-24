package unimagdeportes.service.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mapper.checkin.CheckInMapper;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.policy.CheckInWindowPolicy;
import unimagdeportes.service.reservation.ReservationLock;
import unimagdeportes.service.reservation.ReservationService;
import unimagdeportes.service.reservation.ReservationService.CheckInCandidate;

class CheckInServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-11T16:30:00Z");
    private final UUID user = UUID.randomUUID(), space = UUID.randomUUID(), id = UUID.randomUUID();
    private final ReservationService reservations = mock(ReservationService.class);
    private final AuditService audit = mock(AuditService.class);
    private final CheckInService service = new CheckInService(mock(ReservationLock.class), reservations,
            new CheckInWindowPolicy(), audit, new CheckInMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void lateCheckInDoesNotCheckCurrentOccupancyAndAuditsOnce() {
        var pending = candidate(id, NOW.minusSeconds(1800), ReservationStatus.CONFIRMED, null);
        var completed = candidate(id, pending.endAt(), ReservationStatus.COMPLETED, NOW);
        when(reservations.checkInCandidate(id)).thenReturn(pending);
        when(reservations.registerCheckIn(id, NOW)).thenReturn(completed);
        assertThat(service.confirm(space, id, user).status()).isEqualTo(ReservationStatus.COMPLETED);
        verify(reservations, never()).hasOtherUserInProgress(any(), any(), any());
        verify(audit).record("CHECK_IN", "RESERVATION", id, user);
    }

    @Test
    void firstCheckInAcceptsExactClosingAndRejectsOneNanosecondLater() {
        var pending = candidate(id, NOW.minusSeconds(3600), ReservationStatus.CONFIRMED, null);
        when(reservations.checkInCandidate(id)).thenReturn(pending);
        when(reservations.registerCheckIn(id, NOW))
                .thenReturn(candidate(id, pending.endAt(), ReservationStatus.COMPLETED, NOW));
        assertThat(service.confirm(space, id, user).status()).isEqualTo(ReservationStatus.COMPLETED);
        when(reservations.checkInCandidate(id))
                .thenReturn(candidate(id, pending.endAt().minusNanos(1), ReservationStatus.CONFIRMED, null));
        assertThatThrownBy(() -> service.confirm(space, id, user)).isInstanceOfSatisfying(
                BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("CHECKIN_WINDOW_CLOSED"));
    }

    @Test
    void completedRetryPreservesTimeWithoutWritingEvenAfterTheDeadline() {
        Instant original = NOW.minusSeconds(7200);
        when(reservations.checkInCandidate(id))
                .thenReturn(candidate(id, NOW.minusSeconds(10800), ReservationStatus.COMPLETED, original));
        assertThat(service.confirm(space, id, user).checkedInAt().toInstant()).isEqualTo(original);
        verify(reservations, never()).registerCheckIn(any(), any());
        verify(audit, never()).record(any(), any(), any(), any());
    }

    @Test
    void legacyEndpointRequiresSelectionWhenTwoPendingReservationsAreEligible() {
        when(reservations.checkInCandidates(space, user)).thenReturn(List.of(
                candidate(id, NOW.minusSeconds(1800), ReservationStatus.CONFIRMED, null),
                candidate(UUID.randomUUID(), NOW.plusSeconds(1800), ReservationStatus.CONFIRMED, null)));
        assertThat(service.eligible(space, user)).hasSize(2);
        assertThatThrownBy(() -> service.checkIn(space, user)).isInstanceOfSatisfying(
                BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("CHECKIN_SELECTION_REQUIRED"));
        verify(reservations, never()).registerCheckIn(any(), any());
    }

    @Test
    void explicitConfirmationChecksOwnerAndSpaceBeforeAnyWrite() {
        when(reservations.checkInCandidate(id))
                .thenReturn(candidate(id, NOW.plusSeconds(1800), ReservationStatus.CONFIRMED, null));
        assertThatThrownBy(() -> service.confirm(space, id, UUID.randomUUID())).isInstanceOfSatisfying(
                BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("RESERVATION_NOT_FOUND"));
        assertThatThrownBy(() -> service.confirm(UUID.randomUUID(), id, user)).isInstanceOfSatisfying(
                BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("RESERVATION_NOT_FOUND"));
        verify(reservations, never()).registerCheckIn(any(), any());
    }

    @Test
    void currentOccupancyStillPreventsEarlyCheckIn() {
        when(reservations.checkInCandidate(id))
                .thenReturn(candidate(id, NOW.plusSeconds(3900), ReservationStatus.CONFIRMED, null));
        when(reservations.hasOtherUserInProgress(space, user, NOW)).thenReturn(true);
        assertThatThrownBy(() -> service.confirm(space, id, user)).isInstanceOfSatisfying(
                BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo("FORBIDDEN"));
        verify(reservations, never()).registerCheckIn(eq(id), any());
    }

    private CheckInCandidate candidate(UUID reservationId, Instant end, ReservationStatus status, Instant checkedIn) {
        return new CheckInCandidate(reservationId, user, space, end.minusSeconds(3600), end, status, checkedIn, 10);
    }
}
