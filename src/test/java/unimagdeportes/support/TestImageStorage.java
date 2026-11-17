package unimagdeportes.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.storage.ImageStorage;

/** Test double only: integration tests never use the real Supabase project or credentials. */
public class TestImageStorage implements ImageStorage {
    public final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    public final AtomicInteger uploads = new AtomicInteger();
    public volatile int failUploadAt;
    public volatile boolean failRead;
    public volatile boolean failReadAfterOpen;
    public volatile java.util.function.Consumer<String> beforeUpload = key -> { };
    public volatile boolean failDelete;

    public void reset() {
        objects.clear();
        uploads.set(0);
        failUploadAt = 0;
        failRead = false;
        failReadAfterOpen = false;
        beforeUpload = key -> { };
        failDelete = false;
    }

    @Override
    public void upload(String key, MultipartFile file, String contentType) {
        try {
            beforeUpload.accept(key);
            objects.put(key, file.getBytes());
            // Simulate an ambiguous timeout: the object exists but the client did not see success.
            if (uploads.incrementAndGet() == failUploadAt) throw unavailable();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public InputStream open(String key) {
        if (failRead || !objects.containsKey(key)) throw unavailable();
        if (failReadAfterOpen) return new InputStream() {
            @Override public int read() throws IOException { throw new IOException("Simulated remote read failure"); }
        };
        return new ByteArrayInputStream(objects.get(key));
    }

    @Override
    public void delete(String key) {
        if (failDelete) throw unavailable();
        objects.remove(key);
    }

    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "Storage unavailable");
    }
}
