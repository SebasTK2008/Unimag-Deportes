package unimagdeportes.integration.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.support.AuthTestSupport;

class AuditLogIntegrationTest extends AuthTestSupport {
    @Autowired private AuditService audit;

    @Test
    void persistsEventIdentityActorAndTimestampThroughJpaFieldAccess() throws Exception {
        String token = completeLogin("audit-event@unimagdalena.edu.co");
        UUID actor = UUID.fromString(decoder.decode(token).getSubject());
        UUID reservation = UUID.randomUUID();

        audit.record("RESERVATION_CREATED", "RESERVATION", reservation, actor);

        var stored = jdbc.queryForMap("select * from audit_log where action = 'RESERVATION_CREATED' and entity_id = ?",
                reservation);
        assertThat(stored.get("id")).isInstanceOf(UUID.class);
        assertThat(stored).containsEntry("action", "RESERVATION_CREATED")
                .containsEntry("entity_type", "RESERVATION")
                .containsEntry("entity_id", reservation)
                .containsEntry("user_id", actor)
                .containsEntry("detail", null)
                .containsEntry("created_at", Timestamp.from(clock.instant()));
        assertThat(audit.count("RESERVATION_CREATED", START, START.plusSeconds(1))).isEqualTo(1);
        assertThat(audit.distinctEntities("RESERVATION_CREATED", START, START.plusSeconds(1)))
                .containsExactly(reservation);
    }

    @Test
    void preservesTheSuspensionEndSnapshotUsedByIndicators() throws Exception {
        String token = completeLogin("audit-suspension@unimagdalena.edu.co");
        UUID user = UUID.fromString(decoder.decode(token).getSubject());
        var end = START.plus(Duration.ofDays(7));

        audit.recordSuspension(user, end);

        var stored = jdbc.queryForMap("select * from audit_log where action = 'USER_SUSPENDED' and entity_id = ?", user);
        assertThat(stored).containsEntry("entity_type", "USER")
                .containsEntry("entity_id", user)
                .containsEntry("user_id", null)
                .containsEntry("detail", end.toString())
                .containsEntry("created_at", Timestamp.from(clock.instant()));
        assertThat(audit.countSuspendedUsers(START, end)).isEqualTo(1);
        assertThat(audit.countSuspendedUsers(end, end.plusSeconds(1))).isZero();
    }
}
