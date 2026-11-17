package unimagdeportes.storage;

import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/** Private object storage; callers own and must close downloaded streams. */
public interface ImageStorage {
    void upload(String key, MultipartFile file, String contentType);
    InputStream open(String key);
    void delete(String key);
}
