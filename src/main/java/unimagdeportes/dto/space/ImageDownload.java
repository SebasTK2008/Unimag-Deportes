package unimagdeportes.dto.space;

import java.io.IOException;
import java.io.InputStream;

/** Owns the private download stream; the HTTP caller must close it. */
public record ImageDownload(String contentType, long sizeBytes, InputStream content) implements AutoCloseable {
    @Override
    public void close() throws IOException {
        content.close();
    }
}
