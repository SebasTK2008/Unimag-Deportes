package unimagdeportes.controller.quota;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.dto.quota.QuotaDtos.WeeklyQuota;
import unimagdeportes.security.UserPrincipal;
import unimagdeportes.service.quota.QuotaService;

@RestController
public class QuotaController {
    private final QuotaService quota;

    public QuotaController(QuotaService quota) { this.quota = quota; }

    @GetMapping("/quota/me")
    WeeklyQuota mine(@AuthenticationPrincipal UserPrincipal principal) {
        return quota.currentWeek(principal.id());
    }
}
