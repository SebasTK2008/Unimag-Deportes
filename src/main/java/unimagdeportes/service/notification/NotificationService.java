package unimagdeportes.service.notification;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import unimagdeportes.event.ReservationNotificationEvent;
import unimagdeportes.mail.MailSender;
import unimagdeportes.mail.MailLayout;
import unimagdeportes.service.reservation.ReservationService;

@Service
public class NotificationService {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")
            .withZone(ZoneId.of("America/Bogota"));
    private final MailSender mail;
    private final String contactUrl;
    private final String cancellationUrl;
    private final ReservationService reservations;

    public NotificationService(MailSender mail, ReservationService reservations, @Value("${app.mail.contact-url:}") String contactUrl,
            @Value("${app.mail.cancellation-url:}") String cancellationUrl) {
        this.mail = mail;
        this.reservations = reservations;
        this.contactUrl = contactUrl.trim();
        this.cancellationUrl = cancellationUrl.trim();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotification(ReservationNotificationEvent event) {
        try {
            if (contactUrl.isEmpty()) throw new IllegalStateException("Canal de contacto no configurado");
            String body = switch (event.type()) {
                case REMINDER -> reminder(event);
                case CANCELLATION -> cancellation(event);
                case BLOCK -> "Tu reserva de " + event.spaceName() + " del " + DATE_TIME.format(event.startAt())
                        + " fue cancelada por un bloqueo: " + event.reason()
                        + ". Las horas de esta reserva dejan de contar contra tus límites diario y semanal. "
                        + "Se liberan el espacio y los implementos asociados.";
                case NO_SHOW -> "Se registró una inasistencia para tu reserva de " + event.spaceName() + " del "
                        + DATE_TIME.format(event.startAt()) + ". Sus horas siguen contando en tus límites diario y semanal.";
                case SUSPENSION -> "Tu cuenta fue suspendida. Podrás reservar de nuevo desde "
                        + DATE_TIME.format(event.suspendedUntil()) + ".";
            };
            String title = switch (event.type()) {
                case CANCELLATION, BLOCK -> "Reserva cancelada";
                case REMINDER -> "Recordatorio de tu reserva";
                case NO_SHOW -> "Inasistencia registrada";
                case SUSPENSION -> "Suspensión de tu cuenta";
            };
            mail.send(event.recipient(), title + " — Unimag Deportes",
                    MailLayout.notification(title, event.userName(), body, contactUrl));
            if (event.type() == ReservationNotificationEvent.Type.REMINDER) {
                reservations.markReminderSent(event.reservationId());
            }
        } catch (RuntimeException ex) {
            LOG.error("No fue posible enviar la notificación {}: {}", event.type(), ex.getClass().getSimpleName());
        }
    }

    private String reminder(ReservationNotificationEvent event) {
        if (cancellationUrl.isEmpty() || !cancellationUrl.contains("{id}")) {
            throw new IllegalStateException("Ruta de cancelación no configurada");
        }
        return "Tu reserva de " + event.spaceName() + " comienza el " + DATE_TIME.format(event.startAt())
                + ".\nCancelar: " + cancellationUrl.replace("{id}", event.reservationId().toString());
    }

    private String cancellation(ReservationNotificationEvent event) {
        String body = "Tu reserva de " + event.spaceName() + " del " + DATE_TIME.format(event.startAt())
                + " fue cancelada." + (event.reason() == null ? "" : " Motivo: " + event.reason() + ".");
        var details = event.cancellation();
        if (details == null) return body;
        String effect = switch (details.status()) {
            case CANCELLED_LATE -> "Cancelación tardía: las " + details.reservedHours()
                    + " h de esta reserva siguen contando contra tus límites diario y semanal.";
            case CANCELLED_ON_TIME -> "Cancelación a tiempo: las " + details.reservedHours()
                    + " h de esta reserva dejan de contar contra tus límites diario y semanal.";
            case CANCELLED_BY_SUSPENSION -> "Las " + details.reservedHours()
                    + " h de esta reserva dejan de contar contra tus límites diario y semanal. "
                    + "La cancelación no levanta la suspensión de tu cuenta.";
            default -> throw new IllegalArgumentException("Estado de cancelación no soportado");
        };
        return body + "\n\n" + effect + " Se liberan el espacio y los implementos asociados.";
    }
}
