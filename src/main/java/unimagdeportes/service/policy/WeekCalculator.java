package unimagdeportes.service.policy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

public final class WeekCalculator {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public ZonedDateTime startOfWeek(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(BOGOTA);
    }

    public ZonedDateTime nextWeek(LocalDate date) {
        return startOfWeek(date).plusWeeks(1);
    }
}
