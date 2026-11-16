package unimagdeportes.service.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;
import unimagdeportes.dto.block.BlockDtos.CreateBlock;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mapper.block.BlockMapper;
import unimagdeportes.model.block.BlockType;
import unimagdeportes.repository.block.InstitutionalBlockRepository;
import unimagdeportes.repository.space.SpaceRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.reservation.ReservationLock;
import unimagdeportes.service.reservation.ReservationService;

class BlockServiceTest {
    private final InstitutionalBlockRepository blocks = mock(InstitutionalBlockRepository.class);
    private final SpaceRepository spaces = mock(SpaceRepository.class);
    private final ReservationLock lock = mock(ReservationLock.class);
    private final ReservationService reservations = mock(ReservationService.class);
    private final AuditService audit = mock(AuditService.class);
    private final BlockService service = new BlockService(blocks, spaces, lock, reservations,
            new BlockSchedule(), audit, Mappers.getMapper(BlockMapper.class));
    private final UUID spaceId = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @Test
    void locksBeforeReadingAndCreatesOneRowPerHour() {
        when(spaces.existsById(spaceId)).thenReturn(true);
        when(reservations.cancelForBlock(eq(spaceId), any(), eq("Evento"), eq(admin)))
                .thenReturn(2);

        var result = service.create(request(), admin);

        assertThat(result.createdBlocks()).isEqualTo(2);
        assertThat(result.cancelledReservations()).isEqualTo(2);
        assertThat(result.recurrenceGroup()).isNull();
        var order = inOrder(lock, spaces, reservations);
        order.verify(lock).acquire();
        order.verify(spaces).existsById(spaceId);
        order.verify(reservations).cancelForBlock(eq(spaceId), any(), eq("Evento"), eq(admin));
        verify(blocks, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void doesNotCreateBlocksWhenReservationIsInUse() {
        when(spaces.existsById(spaceId)).thenReturn(true);
        when(reservations.cancelForBlock(eq(spaceId), any(), eq("Evento"), eq(admin)))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "RESERVATION_IN_USE", "En curso."));

        assertThatThrownBy(() -> service.create(request(), admin)).isInstanceOf(BusinessException.class);
        verify(blocks, never()).save(any());
    }

    private CreateBlock request() {
        return new CreateBlock(spaceId, BlockType.EVENT, "Evento", Instant.parse("2026-11-11T13:00:00Z"),
                Instant.parse("2026-11-11T15:00:00Z"), null);
    }
}
