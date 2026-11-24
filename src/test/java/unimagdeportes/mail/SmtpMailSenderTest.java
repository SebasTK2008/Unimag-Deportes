package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.Part;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;

class SmtpMailSenderTest {
    @Test
    void sendsEquivalentTextAndHtmlWithPdfAndPreservesDisplayName() throws Exception {
        var sender = mock(JavaMailSender.class);
        var message = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);
        var body = new MailBody("Reserva confirmada", "<h1>Reserva confirmada</h1>");
        new SmtpMailSender(sender, "deportes@example.org", "Área de Deportes")
                .sendWithPdf("test@example.org", "Reserva confirmada", body, "comprobante.pdf", new byte[] {1, 2});
        message.saveChanges();
        assertThat(findPart(message, "text/plain").getContent().toString()).isEqualTo(body.text());
        assertThat(findPart(message, "text/html").getContent().toString()).isEqualTo(body.html());
        assertThat(findPart(message, "application/pdf").getInputStream().readAllBytes()).containsExactly(1, 2);
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("Área de Deportes");
        verify(sender).send(message);
    }

    @Test
    void retriesRichMailOnTheSameTermsAsPlainMail() throws Exception {
        var sender = mock(JavaMailSender.class);
        var message = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("temporary")).doNothing().when(sender).send(any(MimeMessage.class));
        new SmtpMailSender(sender).send("test@example.org", "Código", new MailBody("001234", "<p>001234</p>"));
        verify(sender, times(2)).send(message);
    }

    private Part findPart(Part part, String type) throws Exception {
        if (part.isMimeType(type)) return part;
        if (part.getContent() instanceof MimeMultipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                Part found = findPart(multipart.getBodyPart(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void configuresAndValidatesSenderAddress() {
        var sender = mock(JavaMailSender.class);
        new SmtpMailSender(sender, "deportes@example.org").send("test@example.org", "Asunto", "Mensaje");
        var captured = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        assertThat(captured.getValue().getFrom()).isEqualTo("deportes@example.org");
        assertThatThrownBy(() -> new SmtpMailSender(sender, "not-an-email")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configuresDisplayNameForTextMail() throws Exception {
        var sender = mock(JavaMailSender.class);
        new SmtpMailSender(sender, "unimagdeportes@gmail.com", "Unimag Deportes")
                .send("test@example.org", "Asunto", "Mensaje");
        var captured = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        var from = new InternetAddress(captured.getValue().getFrom());
        assertThat(from.getAddress()).isEqualTo("unimagdeportes@gmail.com");
        assertThat(from.getPersonal()).isEqualTo("Unimag Deportes");
    }

    @Test
    void rejectsHeaderInjectionAndFormattedAddresses() {
        var sender = mock(JavaMailSender.class);
        for (String name : new String[] {"Unimag\rDeportes", "Unimag\nDeportes", "Unimag Deportes\n"}) {
            assertThatThrownBy(() -> new SmtpMailSender(sender, "deportes@example.org", name))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String address : new String[] {"deportes@example.org\r", "deportes@example.org\n",
                "Unimag Deportes <deportes@example.org>"}) {
            assertThatThrownBy(() -> new SmtpMailSender(sender, address, "Unimag Deportes"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void keepsOptionalSenderForMailpit() {
        var sender = mock(JavaMailSender.class);
        new SmtpMailSender(sender, "", "Unimag Deportes").send("test@example.org", "Asunto", "Mensaje");
        var captured = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        assertThat(captured.getValue().getFrom()).isNull();
    }

    @Test
    void configuresUtf8DisplayNameForPdfMail() throws Exception {
        var sender = mock(JavaMailSender.class);
        var message = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);
        new SmtpMailSender(sender, "deportes@example.org", "Área de Deportes")
                .sendWithPdf("test@example.org", "Comprobante", "Adjunto", "comprobante.pdf", new byte[] {1});
        verify(sender).send(message);
        var from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("deportes@example.org");
        assertThat(from.getPersonal()).isEqualTo("Área de Deportes");
    }
    @Test
    void retriesTransientSmtpFailureAndStopsAfterSuccess() {
        var sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("temporary")).doNothing().when(sender).send(any(SimpleMailMessage.class));
        new SmtpMailSender(sender).send("test@example.org", "Asunto", "Mensaje");
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void limitsSmtpRetriesAndDoesNotRetryPreparationErrors() {
        var sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("unavailable")).when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> new SmtpMailSender(sender).send("test@example.org", "Asunto", "Mensaje"))
                .isInstanceOf(MailSendException.class);
        verify(sender, times(3)).send(any(SimpleMailMessage.class));
        var invalid = mock(JavaMailSender.class);
        doThrow(new MailPreparationException("invalid")).when(invalid).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> new SmtpMailSender(invalid).send("test@example.org", "Asunto", "Mensaje"))
                .isInstanceOf(MailPreparationException.class);
        verify(invalid).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendsPdfAttachmentWithItsNameAndContent() throws Exception {
        var output = new ByteArrayOutputStream();
        var document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        document.add(new Paragraph("Reserva de prueba"));
        document.close();
        byte[] pdf = output.toByteArray();

        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);
        new SmtpMailSender(sender).sendWithPdf("test@unimagdalena.edu.co", "Comprobante",
                "Tu reserva", "comprobante.pdf", pdf);

        verify(sender).send(any(MimeMessage.class));
        message.saveChanges();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("test@unimagdalena.edu.co");
        var root = (MimeMultipart) message.getContent();
        var attachment = root.getBodyPart(1);
        assertThat(attachment.getFileName()).isEqualTo("comprobante.pdf");
        assertThat(attachment.isMimeType("application/pdf")).isTrue();
        assertThat(attachment.getInputStream().readAllBytes()).isEqualTo(pdf);
    }
}
