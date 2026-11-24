package unimagdeportes.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import unimagdeportes.controller.dev.DevLoginController;
import unimagdeportes.service.user.UserService;

class DevProfileTest {
    @Test
    void controllerIsAbsentWithoutDevProfile() {
        try (var context = context(false)) {
            assertThat(context.getBeansOfType(DevLoginController.class)).isEmpty();
        }
    }

    @Test
    void controllerIsRegisteredWithDevProfile() {
        try (var context = context(true)) {
            assertThat(context.getBeansOfType(DevLoginController.class)).hasSize(1);
        }
    }

    private AnnotationConfigApplicationContext context(boolean dev) {
        var context = new AnnotationConfigApplicationContext();
        if (dev) {
            context.getEnvironment().setActiveProfiles("dev");
        }
        context.registerBean(UserService.class, () -> mock(UserService.class));
        context.registerBean(TokenService.class, () -> mock(TokenService.class));
        context.register(DevLoginController.class);
        context.refresh();
        return context;
    }
}
