package unimagdeportes.controller.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.core.JacksonException;

class ReservationErrorHandlerTest {
    private final ReservationErrorHandler handler = new ReservationErrorHandler();

    @Test
    void identifiesTheInvalidFieldWhenTheDecoderProvidesAPath() {
        var cause = JacksonException.wrapWithPath(new IllegalArgumentException(), Object.class, "spaceId");
        var exception = new HttpMessageNotReadableException("invalid", cause, mock(HttpInputMessage.class));

        var response = handler.malformedRequest(exception);

        assertThat(response.getBody().details().get("fields"))
                .isEqualTo(Map.of("spaceId", "formato o valor inválido"));
    }

    @Test
    void identifiesTheBodyWhenNoFieldCanBeDetermined() {
        var exception = new HttpMessageNotReadableException("invalid", mock(HttpInputMessage.class));

        assertThat(handler.malformedRequest(exception).getBody().details().get("fields"))
                .isEqualTo(Map.of("$", "formato o valor inválido"));
    }
}
