package unimagdeportes.service.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.event.ReservationNotificationEvent;
import unimagdeportes.event.ReservationNotificationEvent.Type;
import unimagdeportes.mail.MailSender;
import unimagdeportes.service.reservation.ReservationService;

class NotificationServiceTest {
    @Test
    void failedMarkAfterSmtpAcceptanceCanLeadToDuplicateOnRetry() {
        var mail = mock(MailSender.class);
        var reservations = mock(ReservationService.class);
        var id = UUID.randomUUID();
        doThrow(new IllegalStateException("database unavailable")).when(reservations).markReminderSent(id);
        var service = new NotificationService(mail, reservations, "https://client.example/contact",
                "https://client.example/reservations/{id}/cancel");
        var event = new ReservationNotificationEvent(Type.REMINDER, id, "user@unimagdalena.edu.co",
                "Persona", "Cancha", Instant.parse("2026-11-11T14:00:00Z"), null, null);

        service.onNotification(event);
        service.onNotification(event);

        verify(mail, times(2)).send(anyString(), anyString(), anyString());
        verify(reservations, times(2)).markReminderSent(id);
    }
}
