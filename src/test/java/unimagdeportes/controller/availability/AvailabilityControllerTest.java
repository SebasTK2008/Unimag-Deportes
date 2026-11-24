package unimagdeportes.controller.availability;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import unimagdeportes.exception.GlobalExceptionHandler;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.service.availability.AvailabilityService;
import unimagdeportes.service.availability.AvailabilityStream;

class AvailabilityControllerTest {
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final AvailabilityStream stream = new AvailabilityStream(Clock.systemUTC());
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AvailabilityController(availability, stream))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @AfterEach
    void closeStreams() {
        stream.closeSubscriptions();
    }

    @Test
    void rejectsInvalidDisciplineAndDateWithDocumentedErrors() throws Exception {
        when(availability.spaces("INVALID")).thenThrow(new BusinessException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La disciplina indicada no es válida.", java.util.Map.of("fields", java.util.Map.of("discipline", "valor inválido"))));
        mvc.perform(get("/spaces").param("discipline", "INVALID"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fields.discipline").value("valor inválido"));
        mvc.perform(get("/spaces/{id}/availability", UUID.randomUUID()).param("date", "2026-02-30"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE"));
    }

    @Test
    void streamsEventsOnlyForTheSubscribedSpace() throws Exception {
        UUID id = UUID.randomUUID();
        var result = mvc.perform(get("/spaces/{id}/availability/stream", id).accept("text/event-stream"))
                .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        stream.onAvailabilityChanged(new AvailabilityChangedEvent(UUID.randomUUID()));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("availability-changed");
        stream.onAvailabilityChanged(new AvailabilityChangedEvent(id));
        assertThat(result.getResponse().getContentAsString()).contains("event:availability-changed")
                .contains(id.toString()).contains("changedAt").contains("-05:00");
    }

    @Test
    void notifiesMultipleSubscribers() throws Exception {
        UUID id = UUID.randomUUID();
        var responses = new java.util.ArrayList<org.springframework.test.web.servlet.MvcResult>();
        for (int i = 0; i < 20; i++) {
            responses.add(mvc.perform(get("/spaces/{id}/availability/stream", id).accept("text/event-stream"))
                    .andExpect(request().asyncStarted()).andReturn());
        }
        stream.onAvailabilityChanged(new AvailabilityChangedEvent(id));
        for (var response : responses) {
            assertThat(response.getResponse().getContentAsString()).contains("event:availability-changed");
        }
    }

    @Test
    void completesSubscriptionsWhenApplicationStops() throws Exception {
        var result = mvc.perform(get("/spaces/{id}/availability/stream", UUID.randomUUID())
                .accept("text/event-stream")).andExpect(request().asyncStarted()).andReturn();
        stream.closeSubscriptions();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }
}
