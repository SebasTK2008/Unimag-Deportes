package unimagdeportes.service.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.repository.reservation.ReservationRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.user.UserService;

class ReservationAttendanceTest {
    private static final Instant END = Instant.parse("2026-11-11T12:00:00Z");
    private final ReservationRepository repository = mock(ReservationRepository.class);
    private final UUID id = UUID.randomUUID();
    private final Reservation reservation = new Reservation(id, UUID.randomUUID(), UUID.randomUUID(),
            END.minusSeconds(3600), END, END.minusSeconds(7200), 60, 10);

    @Test
    void noShowRevalidationIncludesOnlyInstantsStrictlyAfterTheDeadline() {
        when(repository.findById(id)).thenReturn(Optional.of(reservation));
        var service = serviceAt(END);
        Instant closing = END.plusSeconds(3600);
        assertThat(service.noShowCandidate(id, closing.minusNanos(1))).isEmpty();
        assertThat(service.noShowCandidate(id, closing)).isEmpty();
        assertThat(service.noShowCandidate(id, closing.plusNanos(1))).isPresent();
        reservation.checkIn(closing);
        assertThat(service.noShowCandidate(id, closing.plusNanos(1))).isEmpty();
    }

    @Test
    void candidateQueryDoesNotRoundAwayTheFirstNanosecondAfterClosing() {
        serviceAt(END.plusSeconds(3600)).closedWithoutCheckIn();
        verify(repository).findClosedWithoutCheckIn(END);
        serviceAt(END.plusSeconds(3600).plusNanos(1)).closedWithoutCheckIn();
        verify(repository).findClosedWithoutCheckIn(END.plusNanos(1000));
    }

    @Test
    void lateRegistrationStoresAttendanceAndCompletesInTheSameOperation() {
        when(repository.findById(id)).thenReturn(Optional.of(reservation));
        var registered = serviceAt(END).registerCheckIn(id, END.plusSeconds(1800));
        assertThat(registered.status()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(registered.checkedInAt()).isEqualTo(END.plusSeconds(1800));
    }

    private ReservationService serviceAt(Instant now) {
        return new ReservationService(repository, mock(ReservationLock.class), Clock.fixed(now, ZoneOffset.UTC),
                mock(UserService.class), mock(SpaceService.class), mock(ApplicationEventPublisher.class),
                mock(AuditService.class));
    }
}
