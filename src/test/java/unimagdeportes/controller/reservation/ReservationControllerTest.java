package unimagdeportes.controller.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import unimagdeportes.dto.reservation.ReservationDtos;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.exception.GlobalExceptionHandler;
import unimagdeportes.service.reservation.ReservationCreationService;

class ReservationControllerTest {
    private final ReservationController controller = new ReservationController(mock(ReservationCreationService.class));

    @Test
    void identifiesMissingOrInvalidFields() {
        var request = new ReservationDtos.Request(null, null, 3, null);
        var error = catchThrowableOfType(() -> controller.create(request, null), BusinessException.class);

        assertThat(error.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.getDetails().get("fields")).isEqualTo(Map.of(
                "spaceId", "es obligatorio", "startAt", "es obligatorio", "slots", "debe ser 1 o 2"));
    }

    @Test
    void identifiesRepeatedEquipment() {
        UUID id = UUID.randomUUID();
        var item = new ReservationDtos.EquipmentRequest(id, 1);
        var request = new ReservationDtos.Request(UUID.randomUUID(), OffsetDateTime.parse("2026-11-11T08:00:00-05:00"),
                1, List.of(item, item));
        var error = catchThrowableOfType(() -> controller.create(request, null), BusinessException.class);

        assertThat(error.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.getDetails().get("fields")).isEqualTo(
                Map.of("equipment[1].equipmentId", "es obligatorio y no debe repetirse"));
    }

    @Test
    void identifiesInvalidUuidAndMalformedBodyOverHttp() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ReservationErrorHandler(), new GlobalExceptionHandler()).build();

        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"spaceId\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.spaceId").value("formato o valor inválido"));
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['$']").value("formato o valor inválido"));
    }
}
