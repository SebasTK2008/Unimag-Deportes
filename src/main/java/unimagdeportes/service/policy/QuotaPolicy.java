package unimagdeportes.service.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class QuotaPolicy {
    public record Interval(Instant start, Instant end) { }

    public Duration usedInWeek(List<Interval> reservations, Instant weekStart, Instant weekEnd) {
        Duration used = Duration.ZERO;
        for (Interval reservation : reservations) {
            Instant start = reservation.start().isAfter(weekStart) ? reservation.start() : weekStart;
            Instant end = reservation.end().isBefore(weekEnd) ? reservation.end() : weekEnd;
            if (end.isAfter(start)) {
                used = used.plus(Duration.between(start, end));
            }
        }
        return used;
    }

    public Duration available(Duration limit, Duration used) {
        return used.compareTo(limit) >= 0 ? Duration.ZERO : limit.minus(used);
    }
}
