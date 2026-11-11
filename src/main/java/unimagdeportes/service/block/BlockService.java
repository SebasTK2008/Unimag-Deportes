package unimagdeportes.service.block;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.model.block.BlockType;
import unimagdeportes.repository.block.InstitutionalBlockRepository;

@Service
public class BlockService {
    private final InstitutionalBlockRepository blocks;

    public BlockService(InstitutionalBlockRepository blocks) { this.blocks = blocks; }

    @Transactional(readOnly = true)
    public List<BlockInfo> overlapping(UUID spaceId, Instant start, Instant end) {
        return blocks.findBySpaceIdAndStartAtLessThanAndEndAtGreaterThan(spaceId, end, start).stream()
                .map(block -> new BlockInfo(block.getId(), block.getSpaceId(), block.getType(), block.getReason(),
                        block.getStartAt(), block.getEndAt(), block.getRecurrenceGroup())).toList();
    }

    public record BlockInfo(UUID id, UUID spaceId, BlockType type, String reason, Instant startAt, Instant endAt,
            UUID recurrenceGroup) { }
}
