package unimagdeportes.service.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.service.space.SpaceService;

class QrServiceTest {
    private final SpaceService spaces = mock(SpaceService.class);
    private final UUID space = UUID.randomUUID();

    @Test
    void permitsHttpsInProductionAndHttpOnlyInDevelopment() {
        var production = new MockEnvironment();
        assertThat(new QrService(spaces, production, "https://client.example").generate(space)).contains("<svg");
        assertThatThrownBy(() -> new QrService(spaces, production, "http://client.example").generate(space))
                .isInstanceOf(BusinessException.class);
        var development = new MockEnvironment();
        development.setActiveProfiles("dev");
        assertThat(new QrService(spaces, development, "http://localhost:3000").generate(space)).contains("<svg");
    }

    @Test
    void rejectsInvalidOrigins() {
        var environment = new MockEnvironment();
        for (String origin : new String[]{"", "https://client.example/path", "https://user@client.example",
                "https://client.example/?secret=value"}) {
            assertThatThrownBy(() -> new QrService(spaces, environment, origin).generate(space))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
