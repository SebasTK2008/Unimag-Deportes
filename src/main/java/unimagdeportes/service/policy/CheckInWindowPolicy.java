package unimagdeportes.service.policy;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class CheckInWindowPolicy {
    public Instant opensAt(Instant start, int openingMinutes) {
        return start.minus(Duration.ofMinutes(openingMinutes));
    }

    public boolean isOpen(Instant now, Instant start, Instant end, int openingMinutes) {
        return !now.isBefore(opensAt(start, openingMinutes)) && now.isBefore(end);
    }
}
