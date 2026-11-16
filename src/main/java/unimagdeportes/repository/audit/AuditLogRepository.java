package unimagdeportes.repository.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unimagdeportes.model.audit.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    long countByActionAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String action, Instant from, Instant to);

    @Query("select distinct a.entityId from AuditLog a where a.action = :action "
            + "and a.createdAt >= :from and a.createdAt < :to and a.entityId is not null")
    List<UUID> findDistinctEntityIds(@Param("action") String action, @Param("from") Instant from,
            @Param("to") Instant to);
}
