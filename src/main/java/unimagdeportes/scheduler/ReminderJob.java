package unimagdeportes.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import unimagdeportes.service.reservation.ReservationService;

@Component
public class ReminderJob {
    private static final Logger LOG = LoggerFactory.getLogger(ReminderJob.class);
    private final ReservationService reservations;

    public ReminderJob(ReservationService reservations) { this.reservations = reservations; }

    @Scheduled(fixedDelay = 60000)
    public synchronized void sendReminders() {
        for (var id : reservations.dueReminders()) {
            try {
                reservations.requestReminder(id);
            } catch (RuntimeException ex) {
                LOG.error("No fue posible procesar el recordatorio {}: {}", id, ex.getClass().getSimpleName());
            }
        }
    }
}
