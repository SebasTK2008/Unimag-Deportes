package unimagdeportes.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record UsageRuleSettings(
        @Value("${app.rules.booking-tolerance-minutes:10}") int bookingToleranceMinutes,
        @Value("${app.rules.late-cancellation-minutes:60}") int lateCancellationMinutes,
        @Value("${app.rules.min-advance-minutes:0}") int minAdvanceMinutes,
        @Value("${app.rules.checkin-opening-minutes:10}") int checkinOpeningMinutes,
        @Value("${app.rules.suspension-days:7}") int suspensionDays) { }
