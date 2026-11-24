package unimagdeportes.mail;

public interface MailSender {
    void send(String recipient, String subject, String body);
    void sendWithPdf(String recipient, String subject, String body, String filename, byte[] pdf);

    default void send(String recipient, String subject, MailBody body) {
        send(recipient, subject, body.text());
    }

    default void sendWithPdf(String recipient, String subject, MailBody body, String filename, byte[] pdf) {
        sendWithPdf(recipient, subject, body.text(), filename, pdf);
    }
}
