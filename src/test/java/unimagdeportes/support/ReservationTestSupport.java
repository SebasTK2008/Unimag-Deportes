package unimagdeportes.support;

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

    protected FixtureEquipment equipment(String name, int stock) {
        UUID id = UUID.randomUUID();
        String uniqueName = name + " " + id;
        jdbc.update("insert into equipment (id,name,stock,status) values (?, ?, ?, 'AVAILABLE')", id, uniqueName, stock);
        return new FixtureEquipment(id, uniqueName);
    }
}
