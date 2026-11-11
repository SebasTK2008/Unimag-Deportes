package unimagdeportes.controller.parameter;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.dto.parameter.ParameterDtos.Rules;
import unimagdeportes.dto.parameter.ParameterDtos.UpdateParameters;
import unimagdeportes.security.UserPrincipal;
import unimagdeportes.service.parameter.ParameterService;

@RestController
public class ParameterController {
    private final ParameterService parameters;

    public ParameterController(ParameterService parameters) { this.parameters = parameters; }

    @GetMapping("/rules")
    Rules rules() { return parameters.getRules(); }

    @GetMapping("/admin/parameters")
    Rules parameters() { return parameters.getParameters(); }

    @PutMapping("/admin/parameters")
    Rules update(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody UpdateParameters body) {
        return parameters.updateParameters(body, principal.id());
    }
}
