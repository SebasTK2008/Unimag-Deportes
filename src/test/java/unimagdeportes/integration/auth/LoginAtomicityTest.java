package unimagdeportes.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.AuthTestSupport;

class LoginAtomicityTest extends AuthTestSupport {
    @Test
    void auditFailureRollsBackCodeConsumptionAndNewUserCreation() throws Exception {
        String email = "rollback@unimagdalena.edu.co";
        requestCode(email);
        String code = code(email);
        rejectLoginAudit();
        try {
            assertThat(verify(email, code).statusCode()).isEqualTo(500);
            assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from login_code where used_at is not null", Integer.class)).isZero();
        } finally {
            allowLoginAudit();
        }
        assertThat(verify(email, code).statusCode()).isEqualTo(200);
    }

    @Test
    void auditFailureAlsoRollsBackReauthenticationCodeConsumption() throws Exception {
        String email = "admin@unimagdalena.edu.co";
        String token = completeLogin(email);
        clock.advance(Duration.ofSeconds(60));
        send("POST", "/auth/reauth/code", null, token);
        String payload = "{\"code\":\"" + code(email) + "\"}";
        rejectLoginAudit();
        try {
            assertThat(send("POST", "/auth/reauth/verify", payload, token).statusCode()).isEqualTo(500);
        } finally {
            allowLoginAudit();
        }
        assertThat(send("POST", "/auth/reauth/verify", payload, token).statusCode()).isEqualTo(200);
    }

    private void rejectLoginAudit() {
        jdbc.execute("create function reject_login_audit() returns trigger language plpgsql as $$ "
                + "begin if NEW.action = 'LOGIN' then raise exception 'test audit failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger reject_login before insert on audit_log for each row execute function reject_login_audit()");
    }

    private void allowLoginAudit() {
        jdbc.execute("drop trigger reject_login on audit_log");
        jdbc.execute("drop function reject_login_audit()");
    }
}
