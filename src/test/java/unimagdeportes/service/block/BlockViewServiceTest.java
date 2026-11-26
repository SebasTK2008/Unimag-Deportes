package unimagdeportes.service.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import unimagdeportes.mapper.block.BlockMapper;
import unimagdeportes.model.block.BlockType;
import unimagdeportes.model.block.InstitutionalBlock;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.user.UserService;

class BlockViewServiceTest {
    private final AuditService audit = mock(AuditService.class);
    private final UserService users = mock(UserService.class);
    private final BlockViewService views = new BlockViewService(audit, users, Mappers.getMapper(BlockMapper.class));

    @Test
    void resolvesBothActorsInOneBatchAndPreservesIds() {
        UUID creator = UUID.randomUUID(), canceller = UUID.randomUUID();
        var first = block();
        var second = block();
        first.cancel(Instant.EPOCH, canceller, "Cancelado");
        when(audit.blockCreators(List.of(first.getId(), second.getId())))
                .thenReturn(Map.of(first.getId(), creator, second.getId(), creator));
        when(users.displayNames(Set.of(creator, canceller))).thenReturn(Map.of(creator, "Ana", canceller, "Carlos"));

        var result = views.describe(List.of(first, second));
        assertThat(result.getFirst().id()).isEqualTo(first.getId());
        assertThat(result.getFirst().createdBy()).isEqualTo(creator);
        assertThat(result.getFirst().createdByName()).isEqualTo("Ana");
        assertThat(result.getFirst().cancelledBy()).isEqualTo(canceller);
        assertThat(result.getFirst().cancelledByName()).isEqualTo("Carlos");
        assertThat(result.get(1).createdByName()).isEqualTo("Ana");
        assertThat(result.get(1).cancelledByName()).isNull();
        verify(audit).blockCreators(List.of(first.getId(), second.getId()));
        verify(users).displayNames(Set.of(creator, canceller));
    }

    @Test
    void missingAuditAndNamesRemainNullWithoutUsingIdsAsNames() {
        var block = block();
        when(audit.blockCreators(any())).thenReturn(Map.of());
        when(users.displayNames(any())).thenReturn(Map.of());
        var active = views.describe(block);
        assertThat(active.createdBy()).isNull();
        assertThat(active.createdByName()).isNull();
        assertThat(active.cancelledByName()).isNull();
        UUID canceller = UUID.randomUUID();
        block.cancel(Instant.EPOCH, canceller, "Cancelado");
        var cancelled = views.describe(block);
        assertThat(cancelled.cancelledBy()).isEqualTo(canceller);
        assertThat(cancelled.cancelledByName()).isNull();
    }

    @Test
    void emptyPageDoesNotQueryActors() {
        assertThat(views.describe(List.of())).isEmpty();
        verifyNoInteractions(audit, users);
    }

    @Test
    void absentProfileDoesNotDiscardKnownCreationIdentity() {
        var block = block();
        UUID creator = UUID.randomUUID();
        when(audit.blockCreators(List.of(block.getId()))).thenReturn(Map.of(block.getId(), creator));
        when(users.displayNames(Set.of(creator))).thenReturn(Map.of());
        var result = views.describe(block);
        assertThat(result.createdBy()).isEqualTo(creator);
        assertThat(result.createdByName()).isNull();
    }

    private InstitutionalBlock block() {
        return new InstitutionalBlock(UUID.randomUUID(), UUID.randomUUID(), BlockType.CLASS, "Clase",
                Instant.EPOCH, Instant.EPOCH.plusSeconds(3600), null);
    }
}
