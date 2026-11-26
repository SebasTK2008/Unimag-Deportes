package unimagdeportes.repository.audit;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unimagdeportes.model.audit.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    interface CreationActor {
        UUID getEntityId();
        UUID getUserId();
    }

    @Query("""
            select distinct a.entityId as entityId, a.userId as userId
            from AuditLog a
            where a.action = 'BLOCK_CREATED' and a.entityType = 'INSTITUTIONAL_BLOCK'
              and a.entityId in :ids
            """)
    List<CreationActor> findBlockCreationActors(@Param("ids") Collection<UUID> ids);

    long countByActionAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String action, Instant from, Instant to);

    @Query("select distinct a.entityId from AuditLog a where a.action = :action "
            + "and a.createdAt >= :from and a.createdAt < :to and a.entityId is not null")
    List<UUID> findDistinctEntityIds(@Param("action") String action, @Param("from") Instant from,
            @Param("to") Instant to);

    // Legacy suspension audits lack an end snapshot; use only their latest preceding historical mark.
    @Query(value = """
            select count(distinct a.entity_id)
            from audit_log a
            left join lateral (
                select s.suspended_until from monthly_suspension s
                where s.user_id = a.entity_id and s.created_at <= a.created_at
                order by s.created_at desc limit 1
            ) history on true
            where a.action = 'USER_SUSPENDED' and a.entity_type = 'USER' and a.created_at < :to
              and coalesce(cast(a.detail as timestamptz), history.suspended_until) > :from
            """, nativeQuery = true)
    long countSuspendedUsers(@Param("from") Instant from, @Param("to") Instant to);
}
