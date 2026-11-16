package unimagdeportes.service.policy;

import java.time.Duration;
import java.time.Instant;

/**
 * Decide si una cancelación es tardía y cuántas horas cuenta contra la cuota.
 * Es pura: recibe valores y devuelve decisiones, sin Spring ni JPA (RN-21, RN-22).
 */
public final class CancellationPolicy {

    /**
     * Tardía cuando la anticipación es inferior al umbral conservado por la reserva,
     * o cuando la franja ya empezó. A exactamente {@code thresholdMinutes} del inicio
     * sigue siendo a tiempo (RN-21).
     */
    public boolean isLate(Instant now, Instant startAt, int thresholdMinutes) {
        return now.isAfter(startAt.minus(Duration.ofMinutes(thresholdMinutes)));
    }

    /**
     * Una franja terminada ya no se puede cancelar: su desenlace es la inasistencia
     * (plan general §8), y cancelarla permitiría evitar la sanción.
     */
    public boolean hasEnded(Instant now, Instant endAt) {
        return !now.isBefore(endAt);
    }

    /** Una cancelación a tiempo devuelve las horas; una tardía conserva la duración completa. */
    public int hoursCounted(Instant startAt, Instant endAt, boolean late) {
        return late ? (int) Duration.between(startAt, endAt).toHours() : 0;
    }
}
