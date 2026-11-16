package unimagdeportes.service.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.dto.block.BlockDtos.CreateBlock;
import unimagdeportes.dto.block.BlockDtos.Recurrence;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.block.BlockType;

class BlockScheduleTest {
    private final BlockSchedule schedule = new BlockSchedule();

    @Test
    void materializesEachHourIncludingLastSelectedDayInBogota() {
        var request = new CreateBlock(UUID.randomUUID(), BlockType.CLASS, "Clase",
                Instant.parse("2026-11-11T13:00:00Z"), Instant.parse("2026-11-11T15:00:00Z"),
                new Recurrence(Set.of(DayOfWeek.WEDNESDAY), LocalDate.of(2026, 11, 18)));

        var result = schedule.materialize(request);

        assertThat(result).hasSize(4);
        assertThat(result.getFirst().start()).isEqualTo(Instant.parse("2026-11-11T13:00:00Z"));
        assertThat(result.getLast().end()).isEqualTo(Instant.parse("2026-11-18T15:00:00Z"));
    }

    @Test
    void rejectsPartialHoursAndEmptyRecurrences() {
        UUID space = UUID.randomUUID();
        assertThatThrownBy(() -> schedule.materialize(new CreateBlock(space, BlockType.EVENT, "Evento",
                Instant.parse("2026-11-11T13:00:00Z"), Instant.parse("2026-11-11T13:30:00Z"), null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> schedule.materialize(new CreateBlock(space, BlockType.EVENT, "Evento",
                Instant.parse("2026-11-11T13:00:00Z"), Instant.parse("2026-11-11T14:00:00Z"),
                new Recurrence(Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 11, 11)))))
                .isInstanceOf(BusinessException.class);
    }
}
