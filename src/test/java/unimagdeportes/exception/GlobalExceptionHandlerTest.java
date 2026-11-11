package unimagdeportes.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void preservesBusinessErrorContract() {
        var response = handler.business(new BusinessException(HttpStatus.CONFLICT, "QUOTA_EXCEEDED",
                "Cuota insuficiente.", Map.of("remainingHours", 1)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiError("QUOTA_EXCEEDED", "Cuota insuficiente.",
                Map.of("remainingHours", 1)));
    }

    @Test
    void unexpectedErrorDoesNotExposeInternalMessage() {
        var response = handler.unexpected(new RuntimeException("internal-secret"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("internal-secret");
    }

    @Test
    void missingRequiredParameterReturnsBadRequestThroughMvc() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(handler).build();
        var response = mvc.perform(get("/test")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("VALIDATION_ERROR");
    }

    @RestController
    static class TestController {
        @GetMapping("/test")
        String get(@RequestParam("date") String date) { return date; }
    }
}
