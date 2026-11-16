package unimagdeportes.dto.block;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import unimagdeportes.model.block.BlockType;

public final class BlockDtos {
    private BlockDtos() { }

    public record CreateBlock(UUID spaceId, BlockType type, String reason, Instant startAt, Instant endAt,
            Recurrence recurrence) { }

    public record Recurrence(Set<DayOfWeek> daysOfWeek, LocalDate until) { }

    public record CreatedBlock(UUID recurrenceGroup, int createdBlocks, int cancelledReservations) { }
}
