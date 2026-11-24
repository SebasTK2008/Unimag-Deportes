package unimagdeportes.mail;

import java.net.URI;
import org.springframework.web.util.HtmlUtils;

/** HTML restricted to email-compatible tables and inline styles; variable values are always escaped. */
public final class MailLayout {
    private MailLayout() { }

    public static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    public static String paragraph(String text) {
        return "<p style=\"margin:0 0 16px;\">" + escape(text).replace("\n", "<br>") + "</p>";
    }

    public static String contact(String url) {
        try {
            URI uri = URI.create(url);
            if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null) {
                return "<p style=\"margin:24px 0 0;\">Contacta al administrador: <a style=\"color:#20395f;\" href=\""
                        + escape(url) + "\">" + escape(url) + "</a></p>";
            }
        } catch (IllegalArgumentException ignored) {
            // An invalid configured URL is displayed as text, never as executable HTML.
        }
        return paragraph("Contacta al administrador: " + url);
    }

    public static String render(String title, String content) {
        return """
                <!doctype html>
                <html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;background:#f3f5f7;color:#253548;font-family:Verdana,Geneva,sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 12px;">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="width:100%%;max-width:600px;background:#ffffff;">
                <tr><td style="padding:24px 28px 18px;border-bottom:3px solid #b2985c;color:#20395f;">
                <p style="font-family:Georgia,serif;font-size:22px;margin:0 0 6px;">Universidad del Magdalena</p>
                <p style="font-size:12px;margin:0;">Unimag Deportes · Bienestar Universitario</p></td></tr>
                <tr><td style="padding:28px;font-size:14px;line-height:1.65;">
                <h1 style="font-size:24px;line-height:1.3;color:#20395f;margin:0 0 24px;">%s</h1>
                %s
                </td></tr>
                <tr><td style="padding:16px 28px;border-top:1px solid #d6dce3;font-size:11px;color:#5b6976;">
                Unimag Deportes · Santa Marta, Colombia</td></tr></table>
                </td></tr></table></body></html>
                """.formatted(escape(title), content);
    }

    public static MailBody notification(String title, String name, String body, String contactUrl) {
        String greeting = "Hola " + name + ":";
        return new MailBody(greeting + "\n\n" + body + "\n\nContacta al administrador: " + contactUrl,
                render(title, paragraph(greeting) + paragraph(body) + contact(contactUrl)));
    }
}
