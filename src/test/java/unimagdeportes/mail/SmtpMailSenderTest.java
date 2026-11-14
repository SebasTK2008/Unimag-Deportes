package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpMailSenderTest {
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
