package unimagdeportes.service.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Reglas de suspensión por inasistencias (RN-38, OQ-07). Es pura: recibe valores y
 * devuelve decisiones, sin Spring ni JPA.
 */
public final class SanctionPolicy {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    /** Mes de la inasistencia, representado por su primer día, según el fin programado en America/Bogota. */
    public LocalDate absenceMonth(Instant endAt) {
        return endAt.atZone(BOGOTA).toLocalDate().withDayOfMonth(1);
    }

    /** Intervalo semiabierto [inicio, fin) del mes calendario local. */
    public MonthRange range(LocalDate absenceMonth) {
        return new MonthRange(absenceMonth.atStartOfDay(BOGOTA).toInstant(),
                absenceMonth.plusMonths(1).atStartOfDay(BOGOTA).toInstant());
    }

    /** Se alcanza el umbral vigente al registrar la falta; la marca mensual evita suspender dos veces. */
    public boolean reachesThreshold(long noShowsInMonth, int threshold) {
        return noShowsInMonth >= threshold;
    }

    /** Una suspensión nueva nunca acorta una vigente: se conserva la fecha más lejana. */
    public Instant suspensionEnd(Instant now, int suspensionDays, Instant currentSuspendedUntil) {
        Instant proposed = now.plus(Duration.ofDays(suspensionDays));
        return currentSuspendedUntil != null && currentSuspendedUntil.isAfter(proposed)
                ? currentSuspendedUntil : proposed;
    }

    public record MonthRange(Instant start, Instant end) { }
}
