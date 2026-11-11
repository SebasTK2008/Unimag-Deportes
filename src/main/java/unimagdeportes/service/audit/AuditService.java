package unimagdeportes.service.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.model.audit.AuditLog;
import unimagdeportes.repository.audit.AuditLogRepository;

@Service
public class AuditService {
    private final AuditLogRepository logs;
    private final Clock clock;

    public AuditService(AuditLogRepository logs, Clock clock) {
        this.logs = logs;
        this.clock = clock;
    }

    @Transactional
    public void record(String action, String entityType, UUID entityId, UUID userId) {
        logs.save(new AuditLog(UUID.randomUUID(), action, entityType, entityId, userId, clock.instant()));
    }
}
