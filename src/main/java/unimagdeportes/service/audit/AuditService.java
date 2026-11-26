package unimagdeportes.service.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    @Transactional
    public void record(String action, String entityType, UUID entityId, UUID userId, String detail) {
        logs.save(new AuditLog(UUID.randomUUID(), action, entityType, entityId, userId, clock.instant(), detail));
    }

    /** Only unambiguous creation actors are attributed; missing/contradictory audits remain unknown. */
    @Transactional(readOnly = true)
    public Map<UUID, UUID> blockCreators(Collection<UUID> blockIds) {
        if (blockIds.isEmpty()) return Map.of();
        Map<UUID, UUID> creators = new HashMap<>();
        var ambiguous = new HashSet<UUID>();
        for (var actor : logs.findBlockCreationActors(blockIds)) {
            if (actor.getUserId() == null) {
                ambiguous.add(actor.getEntityId());
                continue;
            }
            UUID previous = creators.putIfAbsent(actor.getEntityId(), actor.getUserId());
            if (previous != null && !previous.equals(actor.getUserId())) ambiguous.add(actor.getEntityId());
        }
        ambiguous.forEach(creators::remove);
        return Map.copyOf(creators);
    }

    @Transactional
    public void recordSuspension(UUID userId, Instant suspendedUntil) {
        logs.save(new AuditLog(UUID.randomUUID(), "USER_SUSPENDED", "USER", userId, null,
                clock.instant(), suspendedUntil.toString()));
    }

    @Transactional(readOnly = true)
    public long countSuspendedUsers(Instant from, Instant to) {
        return logs.countSuspendedUsers(from, to);
    }

    /** Registros de la acción en el intervalo {@code [from, to)}. */
    @Transactional(readOnly = true)
    public long count(String action, Instant from, Instant to) {
        return logs.countByActionAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(action, from, to);
    }

    /** Entidades distintas afectadas por la acción en el intervalo {@code [from, to)}. */
    @Transactional(readOnly = true)
    public List<UUID> distinctEntities(String action, Instant from, Instant to) {
        return logs.findDistinctEntityIds(action, from, to);
    }
}
