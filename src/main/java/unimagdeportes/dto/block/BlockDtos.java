package unimagdeportes.dto.block;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import unimagdeportes.model.block.BlockType;
import unimagdeportes.model.block.BlockStatus;

public final class BlockDtos {
    private BlockDtos() { }

    public record CreateBlock(UUID spaceId, BlockType type, String reason, Instant startAt, Instant endAt,
            Recurrence recurrence) { }

    public record Recurrence(Set<DayOfWeek> daysOfWeek, LocalDate until) { }

    public record CreatedBlock(UUID recurrenceGroup, int createdBlocks, int cancelledReservations) { }

    public record CancelBlock(String reason) { }

    public record AdminBlock(UUID id, UUID spaceId, BlockType type, String reason, OffsetDateTime startAt,
            OffsetDateTime endAt, UUID recurrenceGroup, BlockStatus status, OffsetDateTime cancelledAt,
            UUID cancelledBy, String cancellationReason, UUID createdBy, String createdByName,
            String cancelledByName) { }

    public record BlockPage(List<AdminBlock> items, int page, int size, long totalElements) {
        public BlockPage { items = List.copyOf(items); }
    }
}
