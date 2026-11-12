package unimagdeportes.controller.contact;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.dto.contact.ContactMessage;
import unimagdeportes.security.UserPrincipal;
import unimagdeportes.service.contact.ContactService;

@RestController
public class ContactController {
    private final ContactService contacts;

    public ContactController(ContactService contacts) { this.contacts = contacts; }

    @PostMapping("/contact")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, String> contact(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ContactMessage message) {
        contacts.send(principal, message);
        return Map.of("message", "Tu mensaje fue enviado al área de deportes.");
    }
}
