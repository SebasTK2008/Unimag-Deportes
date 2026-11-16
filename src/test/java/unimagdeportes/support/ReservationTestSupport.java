package unimagdeportes.support;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Helpers compartidos por las pruebas de reservas (UC03/UC04). */
public abstract class ReservationTestSupport extends RulesTestSupport {

    protected record FixtureEquipment(UUID id, String name) { }

    protected UUID space() {
        UUID id = UUID.randomUUID();
        UUID preview = UUID.randomUUID();
        UUID panorama = UUID.randomUUID();
        jdbc.update("insert into space_image (id,content_type,content) values (?, 'image/png', ?), (?, 'image/png', ?)",
                preview, new byte[]{1}, panorama, new byte[]{1});
        jdbc.update("insert into space (id,name,capacity,opening_time,closing_time,status,preview_image_id,panorama_image_id)"
                + " values (?, 'Cancha', 10, '07:00', '11:00', 'ACTIVE', ?, ?)", id, preview, panorama);
        return id;
    }

    protected UUID space(String name, String... disciplines) {
        UUID id = space();
        jdbc.update("update space set name = ? where id = ?", name, id);
        for (String discipline : disciplines) {
            jdbc.update("insert into space_discipline (space_id, discipline) values (?, ?)", id, discipline);
        }
        return id;
    }

    protected UUID fixtureUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_user (id, email, name, role, created_at) values (?, ?, ?, 'USER', ?)",
                id, name + "@unimagdalena.edu.co", name, Timestamp.from(START));
        return id;
    }

    /** Inserta una reserva en el estado indicado sin pasar por las reglas de UC03. */
    protected UUID fixtureReservation(UUID userId, UUID spaceId, String status, Instant start, Instant end) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into reservation (id, user_id, space_id, start_at, end_at, status, created_at, "
                        + "cancellation_threshold_minutes, checkin_opening_minutes) values (?, ?, ?, ?, ?, ?, ?, 60, 10)",
                id, userId, spaceId, Timestamp.from(start), Timestamp.from(end), status,
                Timestamp.from(start.minus(Duration.ofDays(1))));
        return id;
    }

    protected void fixtureAudit(String action, String entityType, UUID entityId, Instant at) {
        jdbc.update("insert into audit_log (id, action, entity_type, entity_id, created_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), action, entityType, entityId, Timestamp.from(at));
    }

    protected UUID userId(String token) {
        return UUID.fromString(decoder.decode(token).getSubject());
    }

    protected FixtureEquipment equipment(String name, int stock) {
        UUID id = UUID.randomUUID();
        String uniqueName = name + " " + id;
        jdbc.update("insert into equipment (id,name,stock,status) values (?, ?, ?, 'AVAILABLE')", id, uniqueName, stock);
        return new FixtureEquipment(id, uniqueName);
    }
}
