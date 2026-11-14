package unimagdeportes.mail;

public interface MailSender {
    void send(String recipient, String subject, String body);
    void sendWithPdf(String recipient, String subject, String body, String filename, byte[] pdf);
}
