package unimagdeportes.controller.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import unimagdeportes.dto.space.ImageDownload;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.exception.GlobalExceptionHandler;
import unimagdeportes.service.checkin.QrService;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.space.SpaceService.ImageKind;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

class SpaceImageControllerTest {
    private final SpaceService spaces = mock(SpaceService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new SpaceController(spaces, mock(QrService.class)))
            .addFilters(cors())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final UUID id = UUID.randomUUID();

    @Test
    void streamsOriginalContentAndClosesStorageStream() throws Exception {
        var closed = new AtomicBoolean();
        when(spaces.image(id, ImageKind.PREVIEW)).thenReturn(new ImageDownload("image/png", 3,
                new ByteArrayInputStream(new byte[]{1, 2, 3}) {
            @Override public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        }));
        mvc.perform(get("/spaces/{id}/images/PREVIEW", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        assertThat(closed).isTrue();
    }

    @Test
    void storageOpenFailureReturnsJsonInsteadOfImageHeaders() throws Exception {
        when(spaces.image(id, ImageKind.PREVIEW)).thenThrow(new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "Unavailable"));
        mvc.perform(get("/spaces/{id}/images/PREVIEW", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("STORAGE_UNAVAILABLE")));
    }

    @Test
    void readFailureBeforeCommitResetsImageHeadersAndClosesStream() throws Exception {
        var closed = new AtomicBoolean();
        when(spaces.image(id, ImageKind.PREVIEW)).thenReturn(new ImageDownload("image/png", 3, new InputStream() {
            @Override public int read() throws IOException { throw new IOException("upstream detail"); }
            @Override public void close() { closed.set(true); }
        }));
        mvc.perform(get("/spaces/{id}/images/PREVIEW", id).header("Origin", "https://client.example"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().doesNotExist("Content-Length"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://client.example"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("STORAGE_UNAVAILABLE")));
        assertThat(closed).isTrue();
    }

    private CorsFilter cors() {
        var configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("https://client.example");
        configuration.addAllowedMethod("GET");
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsFilter(source);
    }
}
