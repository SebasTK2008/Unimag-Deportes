package unimagdeportes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "app.auth.jwt.secret=test-only-012345678901234567890123456789",
        "app.auth.code.hmac-secret=test-only-012345678901234567890123456789" })
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
