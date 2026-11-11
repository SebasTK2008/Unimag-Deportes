package unimagdeportes.repository.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import unimagdeportes.model.audit.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> { }
