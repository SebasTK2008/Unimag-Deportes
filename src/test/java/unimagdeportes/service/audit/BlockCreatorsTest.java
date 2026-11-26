package unimagdeportes.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.repository.audit.AuditLogRepository;
import unimagdeportes.repository.audit.AuditLogRepository.CreationActor;

class BlockCreatorsTest {
    private final AuditLogRepository logs = mock(AuditLogRepository.class);
    private final AuditService audit = new AuditService(logs, Clock.systemUTC());

    @Test
    void acceptsDuplicatesOfSameActorButRejectsConflictsAndNullActorsInEitherOrder() {
        UUID known = UUID.randomUUID(), conflict = UUID.randomUUID(), unknown = UUID.randomUUID();
        UUID otherOrder = UUID.randomUUID(), creator = UUID.randomUUID();
        var ids = List.of(known, conflict, unknown, otherOrder);
        var actors = List.of(actor(known, creator), actor(known, creator),
                actor(conflict, creator), actor(conflict, UUID.randomUUID()), actor(unknown, creator),
                actor(unknown, null), actor(otherOrder, null), actor(otherOrder, creator));
        when(logs.findBlockCreationActors(ids)).thenReturn(actors);
        assertThat(audit.blockCreators(ids)).containsOnlyKeys(known).containsEntry(known, creator);
    }

    @Test
    void missingAuditsRemainUnknown() {
        var ids = List.of(UUID.randomUUID());
        when(logs.findBlockCreationActors(ids)).thenReturn(List.of());
        assertThat(audit.blockCreators(ids)).isEmpty();
    }

    @Test
    void emptyPageDoesNotQueryRepository() {
        assertThat(audit.blockCreators(List.of())).isEmpty();
        verifyNoInteractions(logs);
    }

    private CreationActor actor(UUID block, UUID user) {
        var actor = mock(CreationActor.class);
        when(actor.getEntityId()).thenReturn(block);
        when(actor.getUserId()).thenReturn(user);
        return actor;
    }
}
