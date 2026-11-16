package unimagdeportes.scheduler;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import unimagdeportes.service.reservation.ReservationService;
import unimagdeportes.service.sanction.SanctionService;

/**
 * Cierra las reservas terminadas cada minuto: las confirmadas sin check-in
 * pasan a inasistencia y las que están en uso pasan a completadas. Cada reserva se procesa
 * en su propia transacción, así que un fallo no detiene las demás y el job puede reintentarse.
 */
@Component
public class ReservationLifecycleJob {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationLifecycleJob.class);
    private final SanctionService sanctions;
    private final ReservationService reservations;

    public ReservationLifecycleJob(SanctionService sanctions, ReservationService reservations) {
        this.sanctions = sanctions;
        this.reservations = reservations;
    }

    @Scheduled(cron = "${app.jobs.reservation-lifecycle-cron:0 * * * * *}")
    public synchronized void closeFinishedReservations() {
        var noShows = sanctions.closedWithoutCheckIn();
        var finished = reservations.finishedInUse();
        int failures = processAll(noShows, sanctions::markNoShow, "la inasistencia")
                + processAll(finished, reservations::complete, "el cierre");
        if (!noShows.isEmpty() || !finished.isEmpty()) {
            LOG.info("Cierre de reservas: {} sin check-in, {} en uso, {} con error",
                    noShows.size(), finished.size(), failures);
        }
    }

    private int processAll(List<UUID> ids, Consumer<UUID> step, String description) {
        int failures = 0;
        for (var id : ids) {
            try {
                step.accept(id);
            } catch (RuntimeException ex) {
                failures++;
                LOG.error("No fue posible procesar {} de la reserva {}: {}", description, id,
                        ex.getClass().getSimpleName());
            }
        }
        return failures;
    }
}
