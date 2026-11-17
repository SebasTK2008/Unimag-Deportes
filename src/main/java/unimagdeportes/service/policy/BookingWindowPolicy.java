package unimagdeportes.service.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public final class BookingWindowPolicy {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    // Seven calendar dates including today, not a rolling duration in hours.
    public static final int MAXIMUM_ADVANCE_DAYS = 6;

    public boolean isBookable(Instant now, Instant start, Duration tolerance, Duration minimumAdvance) {
        if (start.atZone(BOGOTA).toLocalDate()
                .isAfter(now.atZone(BOGOTA).toLocalDate().plusDays(MAXIMUM_ADVANCE_DAYS))) return false;
        if (start.isBefore(now)) return !now.isAfter(start.plus(tolerance));
        return !start.isBefore(now.plus(minimumAdvance));
    }
}
