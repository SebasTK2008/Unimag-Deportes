package unimagdeportes.integration.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import unimagdeportes.dto.mail.Receipt;
import unimagdeportes.event.ReservationCreatedEvent;
import unimagdeportes.event.ReservationNotificationEvent;
import unimagdeportes.event.ReservationNotificationEvent.Type;
import unimagdeportes.support.AuthTestSupport;
import unimagdeportes.model.reservation.ReservationStatus;

class NotificationEventsTest extends AuthTestSupport {
    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate transactions;

    @Test
    void cancellationMailExplainsActualQuotaConsequencesInTextAndHtml() {
        for (var state : List.of(ReservationStatus.CANCELLED_ON_TIME, ReservationStatus.CANCELLED_LATE,
                ReservationStatus.CANCELLED_BY_SUSPENSION)) {
            var event = new ReservationNotificationEvent(Type.CANCELLATION, UUID.randomUUID(),
                    "cancellation-detail@unimagdalena.edu.co", "Persona", "Cancha", START,
                    state == ReservationStatus.CANCELLED_BY_SUSPENSION ? "suspensión" : "Cancelación solicitada por el titular",
                    null, new ReservationNotificationEvent.CancellationDetails(state, 2));
            transactions.executeWithoutResult(status -> events.publishEvent(event));
            String body = mail.bodies.get(event.recipient());
            String html = mail.htmlBodies.get(event.recipient());
            assertThat(body).contains("2 h", "límites diario y semanal", "Se liberan el espacio y los implementos");
            assertThat(html).contains("Reserva cancelada", "2 h", "límites diario y semanal");
            if (state == ReservationStatus.CANCELLED_LATE) assertThat(body).contains("siguen contando", "tardía");
            else assertThat(body).contains("dejan de contar");
            if (state == ReservationStatus.CANCELLED_BY_SUSPENSION) assertThat(body).contains("no levanta la suspensión");
        }
    }

    @Test
    void sendsReceiptOnlyAfterCommitAndIncludesContact() {
        var receipt = new Receipt("receipt@unimagdalena.edu.co", "Persona", "Cancha", START,
                START.plusSeconds(3600), List.of(), "Reglas", "https://client.example/contact");
        transactions.executeWithoutResult(status -> {
            events.publishEvent(new ReservationCreatedEvent(receipt));
            assertThat(mail.bodies).doesNotContainKey(receipt.recipient());
        });
        assertThat(mail.bodies.get(receipt.recipient())).contains("https://client.example/contact");
        mail.bodies.clear();
        transactions.executeWithoutResult(status -> {
            events.publishEvent(new ReservationCreatedEvent(receipt));
            status.setRollbackOnly();
        });
        assertThat(mail.bodies).doesNotContainKey(receipt.recipient());
    }

    @Test
    void sendsEachNotificationWithRequiredDetails() {
        UUID id = UUID.randomUUID();
        for (Type type : Type.values()) {
            var event = new ReservationNotificationEvent(type, id, "notice@unimagdalena.edu.co", "Persona",
                    "Cancha", START, "Mantenimiento", Instant.parse("2026-11-17T05:00:00Z"));
            transactions.executeWithoutResult(status -> events.publishEvent(event));
            String body = mail.bodies.remove(event.recipient());
            assertThat(body).contains("Persona", "https://client.example/contact");
            switch (type) {
                case REMINDER -> assertThat(body).contains(id.toString(), "11/11/2026 07:00");
                case BLOCK -> assertThat(body).contains("Mantenimiento");
                case SUSPENSION -> assertThat(body).contains("17/11/2026 00:00");
                case CANCELLATION -> assertThat(body).contains("cancelada", "11/11/2026 07:00", "Motivo: Mantenimiento");
                case NO_SHOW -> assertThat(body).contains("inasistencia", "11/11/2026 07:00", "siguen contando");
            }
        }
    }

    @Test
    void mailFailureDoesNotRollbackCommittedBusinessChange() {
        mail.failing = true;
        UUID id = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            jdbc.update("insert into app_user (id, email, role, created_at) values (?, ?, ?, ?)",
                    id, "committed@unimagdalena.edu.co", "USER", java.sql.Timestamp.from(START));
            events.publishEvent(new ReservationNotificationEvent(Type.NO_SHOW, UUID.randomUUID(),
                    "committed@unimagdalena.edu.co", "Persona", "Cancha", START, null, null));
        });
        assertThat(jdbc.queryForObject("select count(*) from app_user where id = ?", Integer.class, id)).isEqualTo(1);
    }
}
