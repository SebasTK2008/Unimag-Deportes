package unimagdeportes.service.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import unimagdeportes.config.UsageRuleSettings;
import unimagdeportes.dto.space.SpaceDtos.SpaceInfo;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mapper.availability.AvailabilityMapper;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.model.space.SpaceStatus;
import unimagdeportes.service.block.BlockService;
import unimagdeportes.service.reservation.ReservationService;
import unimagdeportes.service.space.SpaceService;

class AvailabilityServiceTest {
    private final SpaceService spaces = mock(SpaceService.class);
    private final ReservationService reservations = mock(ReservationService.class);
    private final BlockService blocks = mock(BlockService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T04:30:00Z"), ZoneId.of("UTC"));
    private final AvailabilityService service = new AvailabilityService(spaces, reservations, blocks,
            Mappers.getMapper(AvailabilityMapper.class), new UsageRuleSettings(10, 60, 0, 10, 7), clock);
    private final UUID id = UUID.randomUUID();

    @Test
    void horizonUsesBogotaTodayThroughSixDaysLater() {
        when(spaces.get(id)).thenReturn(space(id, SpaceStatus.ACTIVE));
        assertThat(service.calendar(id, LocalDate.parse("2026-11-17"), null).slots()).hasSize(2);
        for (String date : List.of("2026-11-10", "2026-11-18")) {
            assertThatThrownBy(() -> service.calendar(id, LocalDate.parse(date), null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getCode())
                    .isEqualTo("OUTSIDE_CALENDAR_HORIZON");
        }
        assertThat(service.calendar(id, LocalDate.parse("2026-11-11"), null).slots().getFirst()
                .startAt().getOffset().toString()).isEqualTo("-05:00");
    }

    @Test
    void listsOnlyActiveSpacesMatchingDiscipline() {
        when(spaces.list()).thenReturn(List.of(space(id, SpaceStatus.ACTIVE),
                space(UUID.randomUUID(), SpaceStatus.MAINTENANCE)));
        assertThat(service.spaces("TENNIS").spaces()).hasSize(1);
        assertThat(service.spaces("VOLLEYBALL").spaces()).isEmpty();
    }

    @Test
    void rejectsInvalidDiscipline() {
        assertThatThrownBy(() -> service.spaces("INVALID"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode()).isEqualTo("VALIDATION_ERROR");
    }

    private SpaceInfo space(UUID spaceId, SpaceStatus status) {
        return new SpaceInfo(spaceId, "Cancha", 4, null, LocalTime.of(8, 0), LocalTime.of(10, 0),
                status, Set.of(Discipline.TENNIS));
    }
}
