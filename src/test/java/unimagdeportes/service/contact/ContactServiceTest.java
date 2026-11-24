package unimagdeportes.service.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import unimagdeportes.dto.contact.ContactMessage;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mail.MailSender;
import unimagdeportes.model.user.Role;
import unimagdeportes.security.UserPrincipal;

class ContactServiceTest {
    private static final UserPrincipal USER =
            new UserPrincipal(UUID.randomUUID(), "user@unimagdalena.edu.co", Role.USER);
    private static final ContactMessage MESSAGE = new ContactMessage("Consulta", "Mensaje");

    @Test
    void rejectsInvalidRecipientsAtConstruction() {
        var mail = mock(MailSender.class);
        for (String recipient : new String[]{"invalid", "Name <admin@example.org>",
                "a@example.org,b@example.org", "admin@example.org\r\nBcc: attacker@example.org"}) {
            assertThatThrownBy(() -> new ContactService(mail, recipient))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(mail);
    }

    @Test
    void trimsConfiguredRecipientAndUsesAuthenticatedIdentity() {
        var mail = mock(MailSender.class);
        new ContactService(mail, " admin@example.org ").send(USER, MESSAGE);

        verify(mail).send("admin@example.org", "Consulta", "De: user@unimagdalena.edu.co\n\nMensaje");
    }

    @Test
    void emptyRecipientDoesNotSendMail() {
        var mail = mock(MailSender.class);
        var service = new ContactService(mail, "  ");

        assertThatThrownBy(() -> service.send(USER, MESSAGE))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.getCode()).isEqualTo("CONTACT_NOT_CONFIGURED");
                });
        verifyNoInteractions(mail);
    }

    @Test
    void preservesSmtpFailureCauseWithoutExposingItInThePublicMessage() {
        var mail = mock(MailSender.class);
        var failure = new MailSendException("smtp-secret");
        doThrow(failure).when(mail).send("admin@example.org", "Consulta", "De: user@unimagdalena.edu.co\n\nMensaje");
        var service = new ContactService(mail, "admin@example.org");

        assertThatThrownBy(() -> service.send(USER, MESSAGE))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.getCode()).isEqualTo("MAIL_DELIVERY_FAILED");
                    assertThat(error.getMessage()).doesNotContain("smtp-secret");
                    assertThat(error.getCause()).isSameAs(failure);
                });
    }
}
