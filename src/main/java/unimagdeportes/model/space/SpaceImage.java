package unimagdeportes.model.space;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "space_image")
public class SpaceImage {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false, unique = true, length = 512)
    private String storageKey;

    @Column(nullable = false)
    private long sizeBytes;

    protected SpaceImage() { }

    public SpaceImage(String contentType, String storageKey, long sizeBytes) {
        this.id = UUID.randomUUID();
        replace(contentType, storageKey, sizeBytes);
    }

    public void replace(String contentType, String storageKey, long sizeBytes) {
        this.contentType = contentType;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
    }

    public String getContentType() { return contentType; }
    public String getStorageKey() { return storageKey; }
    public long getSizeBytes() { return sizeBytes; }
}
