package unimagdeportes.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.service.reservation.ReservationService;
import unimagdeportes.service.sanction.SanctionService;

class ReservationLifecycleJobTest {
    private final SanctionService sanctions = mock(SanctionService.class);
    private final ReservationService reservations = mock(ReservationService.class);
    private final ReservationLifecycleJob job = new ReservationLifecycleJob(sanctions, reservations);

    @Test
    void oneFailingReservationDoesNotStopTheOthers() {
        UUID failing = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        UUID inUse = UUID.randomUUID();
        when(sanctions.closedWithoutCheckIn()).thenReturn(List.of(failing, next));
        when(reservations.finishedInUse()).thenReturn(List.of(inUse));
        doThrow(new IllegalStateException("db")).when(sanctions).markNoShow(failing);

        job.closeFinishedReservations();

        verify(sanctions).markNoShow(next);
        verify(reservations).complete(inUse);
    }
}
