package unimagdeportes.dto.quota;

import java.time.OffsetDateTime;

public final class QuotaDtos {
    private QuotaDtos() { }

    public record WeeklyQuota(OffsetDateTime weekStart, OffsetDateTime weekEnd, long limitHours, long usedHours,
            long availableHours, OffsetDateTime resetAt) { }
}
