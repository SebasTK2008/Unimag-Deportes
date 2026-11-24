package unimagdeportes.dto.availability;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import unimagdeportes.dto.space.SpaceDtos.SpaceInfo;
import unimagdeportes.model.space.Discipline;

public final class AvailabilityDtos {
    private AvailabilityDtos() { }

    public record SpaceList(List<SpaceInfo> spaces) {
        public SpaceList { spaces = List.copyOf(spaces); }
    }

    public record Calendar(UUID spaceId, LocalDate date, Discipline discipline, List<Slot> slots) {
        public Calendar { slots = List.copyOf(slots); }
    }

    public enum SlotStatus { FREE, OCCUPIED, BLOCKED }

    public record Slot(OffsetDateTime startAt, OffsetDateTime endAt, SlotStatus status,
            boolean bookable, String reason) { }

    public record AvailabilityChanged(UUID spaceId, OffsetDateTime changedAt) { }
}
