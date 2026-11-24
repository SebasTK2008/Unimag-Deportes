package unimagdeportes.service.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import unimagdeportes.dto.availability.AvailabilityDtos.SlotStatus;

public class SlotCatalogPolicy {
    private final BookingWindowPolicy bookingWindow = new BookingWindowPolicy();
    public record Interval(Instant startAt, Instant endAt) {
        public boolean overlaps(Instant start, Instant end) {
            return startAt.isBefore(end) && endAt.isAfter(start);
        }
    }

    public record Block(Interval interval, String reason) { }
    public record SlotDecision(Instant startAt, Instant endAt, SlotStatus status, boolean bookable, String reason) { }

    public List<SlotDecision> catalog(LocalDate date, LocalTime opening, LocalTime closing, ZoneId zone,
            Instant now, Duration tolerance, Duration minimumAdvance, boolean active,
            List<Interval> occupied, List<Block> blocks) {
        List<SlotDecision> slots = new ArrayList<>();
        LocalTime first = opening.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        if (first.isBefore(opening)) {
            if (first.getHour() == 23) return List.of();
            first = first.plusHours(1);
        }
        for (LocalTime time = first; time.plusHours(1).isAfter(time)
                && !time.plusHours(1).isAfter(closing); time = time.plusHours(1)) {
            Instant start = date.atTime(time).atZone(zone).toInstant();
            Instant end = start.plus(Duration.ofHours(1));
            var block = blocks.stream().filter(item -> item.interval().overlaps(start, end)).findFirst();
            boolean isOccupied = occupied.stream().anyMatch(item -> item.overlaps(start, end));
            SlotStatus status = block.isPresent() ? SlotStatus.BLOCKED : isOccupied ? SlotStatus.OCCUPIED : SlotStatus.FREE;
            boolean bookable = active && status == SlotStatus.FREE
                    && bookingWindow.isBookable(now, start, tolerance, minimumAdvance);
            slots.add(new SlotDecision(start, end, status, bookable, block.map(Block::reason).orElse(null)));
        }
        return List.copyOf(slots);
    }
}
