package unimagdeportes.storage;

import jakarta.annotation.PreDestroy;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import unimagdeportes.exception.BusinessException;

@Component
public class SupabaseImageStorage implements ImageStorage, AutoCloseable {
    private final String projectUrl;
    private final String bucket;
    private final String key;
    private final int timeoutMillis;
    private final Duration timeout;
    private final HttpClient client;

    public SupabaseImageStorage(
            @Value("${app.storage.supabase.url:}") String projectUrl,
            @Value("${app.storage.supabase.bucket:space-images}") String bucket,
            @Value("${app.storage.supabase.key:}") String key,
            @Value("${app.storage.supabase.timeout:PT1M}") Duration timeout,
            Environment environment) {
        this.projectUrl = projectUrl.strip().replaceAll("/+$", "");
        this.bucket = bucket;
        this.key = key.strip();
        if (!bucket.matches("[a-zA-Z0-9_-]+") || timeout.toMillis() < 1
                || timeout.compareTo(StorageObjectCleanup.UPLOAD_GRACE) >= 0) {
            throw new IllegalArgumentException("Bucket o timeout de Storage inválido.");
        }
        this.timeoutMillis = Math.toIntExact(timeout.toMillis());
        this.timeout = timeout;
        if (!this.projectUrl.isEmpty()) {
            URI uri = URI.create(this.projectUrl);
            boolean localTest = environment.acceptsProfiles(Profiles.of("dev", "test", "auth-test"))
                    && "http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if ((!localTest && !"https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("Storage requiere un origen HTTPS válido.");
            }
        }
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public void upload(String objectKey, MultipartFile file, String contentType) {
        var request = request("/object/" + bucket + "/" + safeKey(objectKey))
                .header("Content-Type", contentType).header("x-upsert", "false");
        try (var input = file.getInputStream()) {
            var body = HttpRequest.BodyPublishers.fromPublisher(
                    HttpRequest.BodyPublishers.ofInputStream(() -> input), file.getSize());
            send(request.POST(body).build());
        } catch (IOException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        HttpURLConnection connection = connection("GET", "/object/authenticated/" + bucket + "/" + safeKey(objectKey));
        try {
            requireSuccess(connection);
            return new FilterInputStream(connection.getInputStream()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        connection.disconnect();
                    }
                }
            };
        } catch (IOException | RuntimeException exception) {
            connection.disconnect();
            if (exception instanceof BusinessException business) throw business;
            throw unavailable(exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        // The batch endpoint treats an absent object as success, making retries idempotent.
        String safe = safeKey(objectKey);
        var body = HttpRequest.BodyPublishers.ofString("{\"prefixes\":[\"" + safe + "\"]}");
        send(request("/object/" + bucket).header("Content-Type", "application/json").method("DELETE", body).build());
    }

    private HttpRequest.Builder request(String path) {
        requireConfigured();
        var request = HttpRequest.newBuilder(URI.create(projectUrl + "/storage/v1" + path))
                .timeout(timeout).header("apikey", key);
        if (!key.startsWith("sb_secret_")) request.header("Authorization", "Bearer " + key);
        return request;
    }

    private void send(HttpRequest request) {
        var response = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        try {
            int status = response.get(timeout.toNanos(), TimeUnit.NANOSECONDS).statusCode();
            if (status < 200 || status >= 300) throw unavailable(new IOException("Storage HTTP " + status));
        } catch (ExecutionException exception) {
            throw unavailable(exception.getCause());
        } catch (TimeoutException exception) {
            response.cancel(true);
            throw unavailable(exception);
        } catch (InterruptedException exception) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        }
    }

    private HttpURLConnection connection(String method, String path) {
        requireConfigured();
        try {
            var connection = (HttpURLConnection) URI.create(projectUrl + "/storage/v1" + path).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("apikey", key);
            if (!key.startsWith("sb_secret_")) connection.setRequestProperty("Authorization", "Bearer " + key);
            return connection;
        } catch (IOException exception) {
            throw unavailable(exception);
        }
    }

    private void requireConfigured() {
        if (projectUrl.isEmpty() || key.isEmpty() || key.startsWith("sb_publishable_")) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED",
                    "El almacenamiento de imágenes no está configurado.");
        }
    }

    private String safeKey(String objectKey) {
        if (!objectKey.matches("[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*")) {
            throw new IllegalArgumentException("Clave de objeto inválida.");
        }
        return objectKey;
    }

    private void requireSuccess(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw unavailable(new IOException("Storage HTTP " + status));
    }

    @Override
    @PreDestroy
    public void close() {
        client.shutdownNow();
        try {
            client.awaitTermination(Duration.ofSeconds(5));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private BusinessException unavailable(Throwable cause) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                "No fue posible acceder al almacenamiento de imágenes.", cause);
    }
}
