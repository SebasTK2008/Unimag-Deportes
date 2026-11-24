package unimagdeportes.service.block;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import unimagdeportes.dto.block.BlockDtos.CreateBlock;
import unimagdeportes.dto.block.BlockDtos.CreatedBlock;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.mapper.block.BlockMapper;
import unimagdeportes.model.block.BlockType;
import unimagdeportes.model.block.InstitutionalBlock;
import unimagdeportes.repository.block.InstitutionalBlockRepository;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.reservation.ReservationLock;
import unimagdeportes.service.reservation.ReservationService;

@Service
public class BlockService {
    private final InstitutionalBlockRepository blocks;
    private final SpaceService spaces;
    private final ReservationLock lock;
    private final ReservationService reservations;
    private final BlockSchedule schedule;
    private final AuditService audit;
    private final BlockMapper mapper;
    private final ApplicationEventPublisher events;

    public BlockService(InstitutionalBlockRepository blocks, SpaceService spaces, ReservationLock lock,
            ReservationService reservations, BlockSchedule schedule, AuditService audit, BlockMapper mapper,
            ApplicationEventPublisher events) {
        this.blocks = blocks;
        this.spaces = spaces;
        this.lock = lock;
        this.reservations = reservations;
        this.schedule = schedule;
        this.audit = audit;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional
    public CreatedBlock create(CreateBlock request, UUID administratorId) {
        lock.acquire();
        var slots = schedule.materialize(request);
        spaces.get(request.spaceId());
        int cancelled = reservations.cancelForBlock(request.spaceId(), slots, request.reason(), administratorId);
        UUID group = request.recurrence() == null ? null : UUID.randomUUID();
        for (var slot : slots) {
            var block = new InstitutionalBlock(UUID.randomUUID(), request.spaceId(), request.type(), request.reason(),
                    slot.start(), slot.end(), group);
            blocks.save(block);
            audit.record("BLOCK_CREATED", "INSTITUTIONAL_BLOCK", block.getId(), administratorId);
        }
        events.publishEvent(new AvailabilityChangedEvent(request.spaceId()));
        return new CreatedBlock(group, slots.size(), cancelled);
    }

    @Transactional(readOnly = true)
    public List<BlockInfo> overlapping(UUID spaceId, Instant start, Instant end) {
        return blocks.findBySpaceIdAndStartAtLessThanAndEndAtGreaterThan(spaceId, end, start).stream()
                .map(mapper::toInfo).toList();
    }

    public record BlockInfo(UUID id, UUID spaceId, BlockType type, String reason, Instant startAt, Instant endAt,
            UUID recurrenceGroup) { }
}
