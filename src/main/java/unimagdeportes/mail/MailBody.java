package unimagdeportes.mail;

/** Equivalent plain-text and HTML alternatives for the same message. */
public record MailBody(String text, String html) { }
