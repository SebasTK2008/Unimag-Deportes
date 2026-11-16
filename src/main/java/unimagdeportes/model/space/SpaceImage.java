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

    @Column(nullable = false)
    private byte[] content;

    protected SpaceImage() { }

    public SpaceImage(String contentType, byte[] content) {
        this.id = UUID.randomUUID();
        replace(contentType, content);
    }

    public void replace(String contentType, byte[] content) {
        this.contentType = contentType;
        this.content = content.clone();
    }

    public String getContentType() { return contentType; }
    public byte[] getContent() { return content.clone(); }
}
