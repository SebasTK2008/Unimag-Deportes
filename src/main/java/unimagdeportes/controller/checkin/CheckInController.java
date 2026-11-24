package unimagdeportes.controller.checkin;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.dto.checkin.CheckInResponse;
import unimagdeportes.dto.checkin.EligibleCheckInResponse;
import unimagdeportes.security.UserPrincipal;
import unimagdeportes.service.checkin.CheckInService;

@RestController
public class CheckInController {
    private final CheckInService checkIns;

    public CheckInController(CheckInService checkIns) { this.checkIns = checkIns; }

    @PostMapping("/checkin/{spaceId}")
    CheckInResponse checkIn(@PathVariable UUID spaceId, @AuthenticationPrincipal UserPrincipal principal) {
        return checkIns.checkIn(spaceId, principal.id());
    }

    @GetMapping("/checkin/{spaceId}/reservations")
    List<EligibleCheckInResponse> eligible(@PathVariable UUID spaceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return checkIns.eligible(spaceId, principal.id());
    }

    @PostMapping("/checkin/{spaceId}/reservations/{reservationId}")
    CheckInResponse confirm(@PathVariable UUID spaceId, @PathVariable UUID reservationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return checkIns.confirm(spaceId, reservationId, principal.id());
    }
}
