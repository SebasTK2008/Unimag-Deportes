package unimagdeportes.mail;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import unimagdeportes.dto.mail.Receipt;

public final class ReceiptMessage {
    public static final String CHECK_IN_INFORMATION = "La reserva corresponde únicamente a la fecha y el horario indicados. "
            + "Este comprobante no sustituye el check-in: registra tu llegada desde el QR o enlace del espacio. "
            + "Si no tienes conexión, puedes confirmar tu asistencia hasta una hora después de finalizar la reserva, "
            + "incluido ese momento. Ese plazo no amplía el tiempo de uso de la cancha.";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")
            .withZone(ZoneId.of("America/Bogota"));

    private ReceiptMessage() { }

    public static MailBody render(Receipt receipt) {
        String summary = "Espacio: " + receipt.spaceName()
                + "\nInicio: " + DATE_TIME.format(receipt.startAt())
                + "\nFinalización: " + DATE_TIME.format(receipt.endAt()) + "\nHora de Bogotá.";
        String greeting = "Hola " + receipt.userName() + ":";
        String attachment = "Tu reserva ha sido confirmada. Adjuntamos el comprobante de reserva.";
        String rules = "Reglas de cancelación:\n" + receipt.cancellationRules();
        String text = greeting + "\n\nReserva confirmada\n\n" + attachment + "\n\n" + summary
                + "\n\n" + rules + "\n\n" + CHECK_IN_INFORMATION
                + "\n\nContacta al administrador: " + receipt.contactUrl();
        String html = MailLayout.render("Reserva confirmada",
                MailLayout.paragraph(greeting) + MailLayout.paragraph(attachment)
                        + MailLayout.paragraph(summary)
                        + "<h2 style=\"font-size:17px;color:#20395f;\">Reglas de cancelación</h2>"
                        + MailLayout.paragraph(receipt.cancellationRules())
                        + MailLayout.paragraph(CHECK_IN_INFORMATION) + MailLayout.contact(receipt.contactUrl()));
        return new MailBody(text, html);
    }
}
