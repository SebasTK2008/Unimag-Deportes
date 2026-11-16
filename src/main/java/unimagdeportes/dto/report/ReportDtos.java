package unimagdeportes.dto.report;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import unimagdeportes.dto.availability.AvailabilityDtos.SlotStatus;

public final class ReportDtos {
    private ReportDtos() { }

    public record Occupancy(LocalDate date, List<SpaceOccupancy> spaces) {
        public Occupancy { spaces = List.copyOf(spaces); }
    }

    public record SpaceOccupancy(UUID spaceId, String spaceName, List<OccupancySlot> slots) {
        public SpaceOccupancy { slots = List.copyOf(slots); }
    }

    /** {@code reservationId} solo se informa en las franjas {@code OCCUPIED}. */
    public record OccupancySlot(OffsetDateTime startAt, OffsetDateTime endAt, SlotStatus status, UUID reservationId) { }

    public enum Period { WEEKLY, MONTHLY }

    public record Indicators(Period period, OffsetDateTime from, OffsetDateTime to, long reservations,
            long lateCancellations, long noShows, long suspendedUsers) { }
}
