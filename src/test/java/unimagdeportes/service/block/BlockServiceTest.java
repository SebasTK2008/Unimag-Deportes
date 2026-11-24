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
import org.springframework.context.ApplicationEventPublisher;
import unimagdeportes.dto.block.BlockDtos.CreateBlock;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.mapper.block.BlockMapper;
import unimagdeportes.model.block.BlockType;
import unimagdeportes.repository.block.InstitutionalBlockRepository;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.reservation.ReservationLock;
import unimagdeportes.service.reservation.ReservationService;

class BlockServiceTest {
    private final InstitutionalBlockRepository blocks = mock(InstitutionalBlockRepository.class);
    private final SpaceService spaces = mock(SpaceService.class);
    private final ReservationLock lock = mock(ReservationLock.class);
    private final ReservationService reservations = mock(ReservationService.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BlockService service = new BlockService(blocks, spaces, lock, reservations,
            new BlockSchedule(), audit, Mappers.getMapper(BlockMapper.class), events);
    private final UUID spaceId = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @Test
    void locksBeforeReadingAndCreatesOneRowPerHour() {
        when(reservations.cancelForBlock(eq(spaceId), any(), eq("Evento"), eq(admin)))
                .thenReturn(2);

        var result = service.create(request(), admin);

        assertThat(result.createdBlocks()).isEqualTo(2);
        assertThat(result.cancelledReservations()).isEqualTo(2);
        assertThat(result.recurrenceGroup()).isNull();
        var order = inOrder(lock, spaces, reservations);
        order.verify(lock).acquire();
        order.verify(spaces).get(spaceId);
        order.verify(reservations).cancelForBlock(eq(spaceId), any(), eq("Evento"), eq(admin));
        verify(blocks, org.mockito.Mockito.times(2)).save(any());
        verify(events).publishEvent(new AvailabilityChangedEvent(spaceId));
    }

    @Test
    void doesNotCreateBlocksWhenReservationIsInUse() {
        when(reservations.cancelForBlock(eq(spaceId), any(), eq("Evento"), eq(admin)))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "RESERVATION_IN_USE", "En curso."));

        assertThatThrownBy(() -> service.create(request(), admin)).isInstanceOf(BusinessException.class);
        verify(blocks, never()).save(any());
        verify(events, never()).publishEvent(any(AvailabilityChangedEvent.class));
    }

    private CreateBlock request() {
        return new CreateBlock(spaceId, BlockType.EVENT, "Evento", Instant.parse("2026-11-11T13:00:00Z"),
                Instant.parse("2026-11-11T15:00:00Z"), null);
    }
}
