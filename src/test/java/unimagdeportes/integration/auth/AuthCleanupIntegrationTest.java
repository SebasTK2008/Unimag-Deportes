package unimagdeportes.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import unimagdeportes.scheduler.AuthCleanupJob;
import unimagdeportes.support.AuthTestSupport;

class AuthCleanupIntegrationTest extends AuthTestSupport {
    @Autowired AuthCleanupJob cleanup;

    @Test
    void deletesOnlyOldExpiredOrUsedCodesAndOldIpRequests() {
        UUID expired = codeRecord(START.minusSeconds(3600), START, null);
        UUID used = codeRecord(START.minusSeconds(3601), START.plusSeconds(100), START);
        UUID recent = codeRecord(START.minusSeconds(3599), START.minusSeconds(1), START);
        UUID active = codeRecord(START.minusSeconds(7200), START.plusSeconds(1), null);
        jdbc.update("insert into login_code_request_ip (id,ip_address,created_at) values "
                        + "(?, '127.0.0.1', ?), (?, '127.0.0.1', ?)",
                UUID.randomUUID(), Timestamp.from(START.minusSeconds(3600)),
                UUID.randomUUID(), Timestamp.from(START.minusSeconds(3599)));
        cleanup.cleanup();
        assertThat(jdbc.queryForList("select id from login_code", UUID.class)).containsExactlyInAnyOrder(recent, active);
        assertThat(jdbc.queryForObject("select count(*) from login_code where id in (?,?)", Integer.class,
                expired, used)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from login_code_request_ip", Integer.class)).isEqualTo(1);
    }

    @Test
    void cleanupCannotResetTheRollingEmailLimit() throws Exception {
        String email = "cleanup-limit@unimagdalena.edu.co";
        for (int request = 0; request < 5; request++) {
            assertThat(requestCode(email).statusCode()).isEqualTo(202);
            clock.advance(Duration.ofMinutes(1));
        }
        clock.set(START.plus(Duration.ofMinutes(59)));
        cleanup.cleanup();
        error(requestCode(email), 429, "CODE_REQUEST_LIMIT");
        clock.set(START.plus(Duration.ofHours(1)));
        cleanup.cleanup();
        assertThat(requestCode(email).statusCode()).isEqualTo(202);
    }

    private UUID codeRecord(Instant created, Instant expires, Instant used) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into login_code (id,email,code_hash,created_at,expires_at,used_at) values "
                        + "(?, 'cleanup@unimagdalena.edu.co', 'hash', ?, ?, ?)",
                id, Timestamp.from(created), Timestamp.from(expires), used == null ? null : Timestamp.from(used));
        return id;
    }
}
