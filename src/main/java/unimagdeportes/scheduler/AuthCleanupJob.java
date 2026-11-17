package unimagdeportes.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import unimagdeportes.service.auth.LoginCodeService;

@Component
public class AuthCleanupJob {
    private final LoginCodeService codes;

    public AuthCleanupJob(LoginCodeService codes) {
        this.codes = codes;
    }

    @Scheduled(cron = "${app.jobs.auth-cleanup-cron:0 0 * * * *}")
    public void cleanup() {
        codes.cleanupExpired();
    }
}
