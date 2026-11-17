package unimagdeportes.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import unimagdeportes.dto.availability.AvailabilityDtos.SlotStatus;
import unimagdeportes.service.policy.SlotCatalogPolicy.Block;
import unimagdeportes.service.policy.SlotCatalogPolicy.Interval;

class SlotCatalogPolicyTest {
    private final SlotCatalogPolicy policy = new SlotCatalogPolicy();
    private final Instant start = Instant.parse("2026-11-11T13:00:00Z");

    @Test
    void derivesSlotsWithHalfOpenOverlapAndVisibleBlockReason() {
        var occupied = List.of(new Interval(start.plusSeconds(3600), start.plusSeconds(7200)));
        var blocks = List.of(new Block(new Interval(start.plusSeconds(7200), start.plusSeconds(10800)), "Clase"));
        var result = catalog(start, true, occupied, blocks);

        assertThat(result).hasSize(3);
        assertThat(result).extracting("status")
                .containsExactly(SlotStatus.FREE, SlotStatus.OCCUPIED, SlotStatus.BLOCKED);
        assertThat(result).extracting("bookable").containsExactly(true, false, false);
        assertThat(result.get(2).reason()).isEqualTo("Clase");
        assertThat(result.get(0).reason()).isNull();
    }

    @Test
    void toleranceIncludesItsEndAndMaintenanceIsNeverBookable() {
        assertThat(catalog(start.plusSeconds(600), true, List.of(), List.of()).getFirst().bookable()).isTrue();
        assertThat(catalog(start.plusSeconds(601), true, List.of(), List.of()).getFirst().bookable()).isFalse();
        assertThat(catalog(start.minusSeconds(1), false, List.of(), List.of()))
                .allMatch(slot -> !slot.bookable());
    }

    @Test
    void alignsSlotsToWholeHoursAfterOpening() {
        var result = policy.catalog(LocalDate.parse("2026-11-11"), LocalTime.of(8, 30), LocalTime.of(11, 30),
                ZoneId.of("America/Bogota"), start, Duration.ofMinutes(10), Duration.ZERO, true, List.of(), List.of());

        assertThat(result).extracting(SlotCatalogPolicy.SlotDecision::startAt)
                .containsExactly(Instant.parse("2026-11-11T14:00:00Z"), Instant.parse("2026-11-11T15:00:00Z"));
    }

    private List<SlotCatalogPolicy.SlotDecision> catalog(Instant now, boolean active,
            List<Interval> occupied, List<Block> blocks) {
        return policy.catalog(LocalDate.parse("2026-11-11"), LocalTime.of(8, 0), LocalTime.of(11, 30),
                ZoneId.of("America/Bogota"), now, Duration.ofMinutes(10), Duration.ZERO, active, occupied, blocks);
    }
}
