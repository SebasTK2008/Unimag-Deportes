package unimagdeportes.integration.blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import unimagdeportes.dto.block.BlockDtos.CreateBlock;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.block.BlockType;
import unimagdeportes.service.block.BlockService;
import unimagdeportes.support.RulesTestSupport;

class BlockIntegrationTest extends RulesTestSupport {
    @Autowired BlockService blocks;
    @Autowired TransactionTemplate transactions;

    @Test
    void createsBlocksCancelsFutureReservationAndNotifiesAfterCommit() throws Exception {
        String admin = administrator();
        String user = completeLogin("usuario@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID space = space();
        UUID reservation = reservation(space, userId, "2026-11-11T14:00:00Z", "2026-11-11T15:00:00Z");
        mail.sentCount.set(0);

        var response = send("POST", "/admin/blocks", payload(space, "2026-11-11T14:00:00Z",
                "2026-11-11T16:00:00Z"), admin);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(body(response).get("createdBlocks").asInt()).isEqualTo(2);
        assertThat(body(response).get("cancelledReservations").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from reservation where id = ?", String.class, reservation))
                .isEqualTo("CANCELLED_BY_ADMIN");
        assertThat(jdbc.queryForObject("select count(*) from institutional_block where space_id = ?",
                Integer.class, space)).isEqualTo(2);
        assertThat(mail.sentCount.get()).isEqualTo(1);
        assertThat(mail.bodies.get("usuario@unimagdalena.edu.co")).contains("Evento");
    }

    @Test
    void inProgressReservationRejectsAllRowsAndLeavesFutureReservationIntact() throws Exception {
        String admin = administrator();
        String user = completeLogin("usuario@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID space = space();
        reservation(space, userId, "2026-11-11T11:00:00Z", "2026-11-11T12:10:00Z");
        UUID future = reservation(space, userId, "2026-11-11T12:30:00Z", "2026-11-11T13:30:00Z");

        var response = send("POST", "/admin/blocks", payload(space, "2026-11-11T11:00:00Z",
                "2026-11-11T13:00:00Z"), admin);

        error(response, 409, "RESERVATION_IN_USE");
        assertThat(jdbc.queryForObject("select status from reservation where id = ?", String.class, future))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select count(*) from institutional_block where space_id = ?",
                Integer.class, space)).isZero();
    }

    @Test
    void reservationStartingBeforeCommitRollsBackBlocksCancellationAuditAndNotice() throws Exception {
        String admin = administrator();
        String user = completeLogin("usuario@unimagdalena.edu.co");
        UUID administratorId = UUID.fromString(decoder.decode(admin).getSubject());
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID space = space();
        UUID reservation = reservation(space, userId, "2026-11-11T14:00:00Z", "2026-11-11T15:00:00Z");
        mail.sentCount.set(0);
        var request = new CreateBlock(space, BlockType.EVENT, "Evento",
                Instant.parse("2026-11-11T14:00:00Z"), Instant.parse("2026-11-11T15:00:00Z"), null);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            blocks.create(request, administratorId);
            clock.set(Instant.parse("2026-11-11T14:00:00Z"));
        })).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode()).isEqualTo("RESERVATION_IN_USE");

        assertThat(jdbc.queryForObject("select status from reservation where id = ?", String.class, reservation))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("select count(*) from institutional_block where space_id = ?",
                Integer.class, space)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id = ?", Integer.class, reservation))
                .isZero();
        assertThat(mail.sentCount.get()).isZero();
    }

    private UUID space() {
        UUID space = UUID.randomUUID();
        UUID preview = UUID.randomUUID();
        UUID panorama = UUID.randomUUID();
        jdbc.update("insert into space_image (id,content_type,storage_key,size_bytes) "
                + "values (?, 'image/png', ?, 1), (?, 'image/png', ?, 1)",
                preview, "fixtures/" + preview, panorama, "fixtures/" + panorama);
        jdbc.update("insert into space (id, name, capacity, opening_time, closing_time, status, preview_image_id, panorama_image_id) "
                + "values (?, 'Cancha', 10, '06:00', '22:00', 'ACTIVE', ?, ?)", space, preview, panorama);
        return space;
    }

    private UUID reservation(UUID space, UUID user, String start, String end) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into reservation (id,user_id,space_id,start_at,end_at,status,created_at,"
                + "cancellation_threshold_minutes,checkin_opening_minutes) "
                + "values (?, ?, ?, ?::timestamptz, ?::timestamptz, 'CONFIRMED', now(), 60, 10)",
                id, user, space, start, end);
        return id;
    }

    private String payload(UUID space, String start, String end) {
        return "{\"spaceId\":\"" + space + "\",\"type\":\"EVENT\",\"reason\":\"Evento\",\"startAt\":\""
                + start + "\",\"endAt\":\"" + end + "\"}";
    }
}
