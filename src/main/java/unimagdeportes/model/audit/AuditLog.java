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
    // Hibernate reads these fields directly; audit queries do not require Java getters.
    @SuppressWarnings("unused")
    private String action;
    @SuppressWarnings("unused")
    private String entityType;
    @SuppressWarnings("unused")
    private UUID entityId;
    @SuppressWarnings("unused")
    private UUID userId;
    @SuppressWarnings("unused")
    private String detail;
    @SuppressWarnings("unused")
    private Instant createdAt;

    protected AuditLog() { }

    public AuditLog(UUID id, String action, String entityType, UUID entityId, UUID userId, Instant createdAt) {
        this(id, action, entityType, entityId, userId, createdAt, null);
    }

    public AuditLog(UUID id, String action, String entityType, UUID entityId, UUID userId, Instant createdAt,
            String detail) {
        this.id = id;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.userId = userId;
        this.createdAt = createdAt;
        this.detail = detail;
    }
}
