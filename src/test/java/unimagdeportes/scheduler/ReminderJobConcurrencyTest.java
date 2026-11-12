package unimagdeportes.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import unimagdeportes.service.reservation.ReservationService;

class ReminderJobConcurrencyTest {
    @Test
    void secondExecutionWaitsForFirstToFinish() throws Exception {
        var reservations = mock(ReservationService.class);
        var firstEntered = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(reservations.dueReminders()).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                firstEntered.countDown();
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) throw new AssertionError("No se liberó la primera ejecución");
            } else {
                secondEntered.countDown();
            }
            return List.of();
        });
        var job = new ReminderJob(reservations);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(job::sendReminders);
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                secondStarted.countDown();
                job.sendReminders();
            });
            try {
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(secondEntered.await(300, TimeUnit.MILLISECONDS)).isFalse();
            } finally {
                releaseFirst.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(secondEntered.getCount()).isZero();
        }
    }
}
