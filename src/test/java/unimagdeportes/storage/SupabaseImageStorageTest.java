package unimagdeportes.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import unimagdeportes.exception.BusinessException;

class SupabaseImageStorageTest {
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final List<SupabaseImageStorage> clients = new java.util.ArrayList<>();
    private HttpServer server;
    private String url;
    private volatile int status = 200;
    private volatile String location;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("apikey"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("x-upsert"), exchange.getRequestBody().readAllBytes()));
            byte[] response = "image-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (location != null) exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        clients.forEach(SupabaseImageStorage::close);
        server.stop(0);
    }

    @Test
    void uploadsDownloadsAndDeletesPrivatelyWithSecretKey() throws Exception {
        var storage = storage("sb_secret_test");
        var file = new MockMultipartFile("image", "untrusted.jpg", "text/plain", new byte[]{1, 2}) {
            @Override public byte[] getBytes() { throw new AssertionError("Must stream upload"); }
        };
        storage.upload("spaces/id/preview/object", file, "image/png");
        try (var input = storage.open("spaces/id/preview/object")) {
            assertThat(input.readAllBytes()).isEqualTo("image-content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        storage.delete("spaces/id/preview/object");

        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).method()).isEqualTo("POST");
        assertThat(requests.get(0).path()).isEqualTo("/storage/v1/object/space-images/spaces/id/preview/object");
        assertThat(requests.get(0).body()).containsExactly((byte) 1, (byte) 2);
        assertThat(requests.get(0).contentType()).isEqualTo("image/png");
        assertThat(requests.get(0).upsert()).isEqualTo("false");
        assertThat(requests.get(1).path()).contains("/object/authenticated/space-images/");
        assertThat(requests.get(2).method()).isEqualTo("DELETE");
        assertThat(new String(requests.get(2).body(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("{\"prefixes\":[\"spaces/id/preview/object\"]}");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.apiKey()).isEqualTo("sb_secret_test");
            assertThat(request.authorization()).isNull();
        });
    }

    @Test
    void supportsLegacyServiceRoleBearer() {
        storage("legacy-service-role-jwt").delete("spaces/id/preview/object");
        assertThat(requests.getFirst().authorization()).isEqualTo("Bearer legacy-service-role-jwt");
    }

    @Test
    void sanitizesUpstreamErrorsAndDoesNotFollowRedirects() {
        for (int error : new int[]{400, 401, 403, 404, 429, 500, 503, 302}) {
            status = error;
            location = url + "/unexpected";
            assertThatThrownBy(() -> storage("sb_secret_test").open("spaces/id/preview/object"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("No fue posible acceder al almacenamiento de imágenes.");
        }
        assertThat(requests).hasSize(8).noneSatisfy(request ->
                assertThat(request.path()).isEqualTo("/unexpected"));
        assertThatThrownBy(() -> storage("sb_secret_test").upload("spaces/id/preview/object",
                new MockMultipartFile("image", new byte[]{1}), "image/png")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage("sb_secret_test").delete("spaces/id/preview/object"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void requiresConfigurationAndRejectsTraversalAndInsecureOrigins() {
        assertThatThrownBy(() -> storage("").open("spaces/id/preview/object"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode()).isEqualTo("STORAGE_NOT_CONFIGURED");
        assertThatThrownBy(() -> storage("sb_publishable_public").delete("spaces/id/preview/object"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage("sb_secret_test").open("../bucket/file"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SupabaseImageStorage(url, "space-images", "sb_secret_test",
                Duration.ofSeconds(1), new MockEnvironment())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SupabaseImageStorage("https://example.org/path", "space-images", "",
                Duration.ofSeconds(1), new MockEnvironment())).isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    @Timeout(10)
    void uploadHasAnOverallDeadlineEvenWhenTheServerDoesNotConsumeTheBody() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.createContext("/storage/v1/object/space-images/blocked", exchange -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        var storage = new SupabaseImageStorage(url, "space-images", "sb_secret_test", Duration.ofMillis(500),
                new MockEnvironment().withProperty("spring.profiles.active", "test"));
        clients.add(storage);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor.submit(() -> storage.upload("blocked",
                    new MockMultipartFile("image", new byte[15 * 1024 * 1024]), "image/png"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(BusinessException.class);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void rejectsTimeoutsThatCanOutliveTheCleanupGrace() {
        assertThatThrownBy(() -> new SupabaseImageStorage(url, "space-images", "",
                Duration.ofHours(1), new MockEnvironment().withProperty("spring.profiles.active", "test")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(10)
    void deletionDeadlineAlsoCoversAStalledResponseBody() throws Exception {
        var release = new CountDownLatch(1);
        server.removeContext("/");
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write(1);
            exchange.getResponseBody().flush();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        var storage = new SupabaseImageStorage(url, "space-images", "sb_secret_test", Duration.ofMillis(500),
                new MockEnvironment().withProperty("spring.profiles.active", "test"));
        clients.add(storage);
        try {
            assertThatThrownBy(() -> storage.delete("stalled")).isInstanceOf(BusinessException.class)
                    .hasRootCauseInstanceOf(java.util.concurrent.TimeoutException.class);
        } finally {
            release.countDown();
        }
    }

    private SupabaseImageStorage storage(String key) {
        var storage = new SupabaseImageStorage(url, "space-images", key, Duration.ofSeconds(2),
                new MockEnvironment().withProperty("spring.profiles.active", "test"));
        clients.add(storage);
        return storage;
    }

    private record Request(String method, String path, String apiKey, String authorization,
            String contentType, String upsert, byte[] body) { }
}
