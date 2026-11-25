package unimagdeportes.service.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import unimagdeportes.dto.block.BlockDtos.CancelBlock;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.mapper.block.BlockMapper;
import unimagdeportes.model.block.BlockStatus;
import unimagdeportes.model.block.BlockType;
import unimagdeportes.model.block.InstitutionalBlock;
import unimagdeportes.repository.block.InstitutionalBlockRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.reservation.ReservationLock;

class BlockCancellationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00.123456789Z");
    private final InstitutionalBlockRepository blocks = mock(InstitutionalBlockRepository.class);
    private final ReservationLock lock = mock(ReservationLock.class);
    private final Clock clock = mock(Clock.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BlockCancellationService service = new BlockCancellationService(blocks, lock, clock, audit,
            events, Mappers.getMapper(BlockMapper.class));
    private final UUID admin = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();

    @Test
    void locksBeforeReadingStateAndClockAndPreservesOriginalData() {
        var block = block(NOW.minusSeconds(10), NOW.plusSeconds(3600));
        when(blocks.findById(id)).thenReturn(Optional.of(block));
        when(clock.instant()).thenReturn(NOW);
        var result = service.cancel(id, new CancelBlock("  Clase cancelada  "), admin);

        var order = inOrder(lock, blocks, clock);
        order.verify(lock).acquire();
        order.verify(blocks).findById(id);
        order.verify(clock).instant();
        assertThat(result.status()).isEqualTo(BlockStatus.CANCELLED);
        assertThat(result.cancelledAt().toInstant()).isEqualTo(Instant.parse("2026-11-11T12:00:00.123456Z"));
        assertThat(result.reason()).isEqualTo("Clase original");
        assertThat(result.startAt().toInstant()).isEqualTo(block.getStartAt());
        assertThat(result.recurrenceGroup()).isEqualTo(block.getRecurrenceGroup());
        assertThat(result.cancellationReason()).isEqualTo("Clase cancelada");
        verify(audit).record("BLOCK_CANCELLED", "INSTITUTIONAL_BLOCK", id, admin, "Clase cancelada");
        verify(events).publishEvent(new AvailabilityChangedEvent(block.getSpaceId()));
    }

    @Test
    void repeatedCancellationNeverOverwritesFirstActorDateOrReason() {
        var block = block(NOW.minusSeconds(3600), NOW.minusSeconds(1));
        block.cancel(NOW.minusSeconds(100), admin, "Primero");
        when(blocks.findById(id)).thenReturn(Optional.of(block));
        var result = service.cancel(id, new CancelBlock("Segundo"), UUID.randomUUID());
        assertThat(result.cancelledBy()).isEqualTo(admin);
        assertThat(result.cancellationReason()).isEqualTo("Primero");
        assertThat(result.cancelledAt().toInstant()).isEqualTo(NOW.minusSeconds(100));
        verify(clock, never()).instant();
        verifyNoInteractions(events);
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void exactEndIsNotCancellable() {
        when(blocks.findById(id)).thenReturn(Optional.of(block(NOW.minusSeconds(3600), NOW)));
        when(clock.instant()).thenReturn(NOW);
        assertThatThrownBy(() -> service.cancel(id, new CancelBlock("Terminó"), admin))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode()).isEqualTo("BLOCK_NOT_CANCELLABLE");
        verifyNoInteractions(events);
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void missingBlockHasDomainError() {
        when(blocks.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(id, new CancelBlock("No existe"), admin))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode()).isEqualTo("BLOCK_NOT_FOUND");
    }

    @Test
    void invalidBodiesAreRejectedEvenBeforeIdempotency() {
        for (CancelBlock request : new CancelBlock[]{null, new CancelBlock(null), new CancelBlock(" \t\n "),
                new CancelBlock("a".repeat(1001))}) {
            assertThatThrownBy(() -> service.cancel(id, request, admin)).isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getCode()).isEqualTo("VALIDATION_ERROR");
        }
        verify(blocks, never()).findById(any());
    }

    @Test
    void unicodeReasonLimitCountsCharactersNotUtf16Units() {
        when(blocks.findById(id)).thenReturn(Optional.of(block(NOW, NOW.plusSeconds(3600))));
        when(clock.instant()).thenReturn(NOW);
        assertThat(service.cancel(id, new CancelBlock("🏀".repeat(1000)), admin).cancellationReason())
                .isEqualTo("🏀".repeat(1000));
    }

    private InstitutionalBlock block(Instant start, Instant end) {
        return new InstitutionalBlock(id, UUID.randomUUID(), BlockType.CLASS, "Clase original",
                start, end, UUID.randomUUID());
    }
}
