package unimagdeportes.model.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    private UUID id;
    private String action;
    private String entityType;
    private UUID entityId;
    private UUID userId;
    private String detail;
    private Instant createdAt;

    protected AuditLog() { }

    public AuditLog(UUID id, String action, String entityType, UUID entityId, UUID userId, Instant createdAt) {
        this.id = id;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.userId = userId;
        this.createdAt = createdAt;
    }
}
