package unimagdeportes.service.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public final class BookingWindowPolicy {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final int MAXIMUM_ADVANCE_DAYS = 7;

    public boolean isBookable(Instant now, Instant start, Duration tolerance, Duration minimumAdvance) {
        if (start.atZone(BOGOTA).toLocalDate()
                .isAfter(now.atZone(BOGOTA).toLocalDate().plusDays(MAXIMUM_ADVANCE_DAYS))) return false;
        if (start.isBefore(now)) return !now.isAfter(start.plus(tolerance));
        return !start.isBefore(now.plus(minimumAdvance));
    }
}
