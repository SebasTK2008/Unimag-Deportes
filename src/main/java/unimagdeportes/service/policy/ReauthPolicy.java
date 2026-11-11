package unimagdeportes.service.policy;

import java.time.Duration;
import java.time.Instant;

public class ReauthPolicy {
    public boolean isValid(Instant reauthenticatedAt, Instant now) {
        return reauthenticatedAt != null && !reauthenticatedAt.isAfter(now)
                && now.isBefore(reauthenticatedAt.plus(Duration.ofMinutes(10)));
    }
}
