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
                case CANCELLATION -> "Tu reserva de " + event.spaceName() + " fue cancelada.";
                case BLOCK -> "Tu reserva de " + event.spaceName() + " fue cancelada por un bloqueo: " + event.reason();
                case NO_SHOW -> "Se registró una inasistencia para tu reserva de " + event.spaceName() + ".";
                case SUSPENSION -> "Tu cuenta fue suspendida. Podrás reservar de nuevo desde "
                        + DATE_TIME.format(event.suspendedUntil()) + ".";
            };
            mail.send(event.recipient(), "Notificación de Unimag Deportes",
                    "Hola " + event.userName() + ":\n\n" + body
                            + "\n\nContacta al administrador: " + contactUrl);
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
}
