package unimagdeportes.service.availability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.config.UsageRuleSettings;
import unimagdeportes.dto.availability.AvailabilityDtos.Calendar;
import unimagdeportes.dto.availability.AvailabilityDtos.SpaceList;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mapper.availability.AvailabilityMapper;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.model.space.SpaceStatus;
import unimagdeportes.service.block.BlockService;
import unimagdeportes.service.policy.BookingWindowPolicy;
import unimagdeportes.service.policy.SlotCatalogPolicy;
import unimagdeportes.service.policy.SlotCatalogPolicy.Block;
import unimagdeportes.service.policy.SlotCatalogPolicy.Interval;
import unimagdeportes.service.reservation.ReservationService;
import unimagdeportes.service.space.SpaceService;

@Service
public class AvailabilityService {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final SpaceService spaces;
    private final ReservationService reservations;
    private final BlockService blocks;
    private final AvailabilityMapper mapper;
    private final UsageRuleSettings settings;
    private final Clock clock;
    private final SlotCatalogPolicy policy = new SlotCatalogPolicy();

    public AvailabilityService(SpaceService spaces, ReservationService reservations, BlockService blocks,
            AvailabilityMapper mapper, UsageRuleSettings settings, Clock clock) {
        this.spaces = spaces;
        this.reservations = reservations;
        this.blocks = blocks;
        this.mapper = mapper;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SpaceList spaces(String requestedDiscipline) {
        Discipline discipline = discipline(requestedDiscipline);
        return new SpaceList(spaces.list().stream()
                .filter(space -> space.status() == SpaceStatus.ACTIVE)
                .filter(space -> discipline == null || space.disciplines().contains(discipline)).toList());
    }

    @Transactional(readOnly = true)
    public Calendar calendar(UUID spaceId, LocalDate date, String requestedDiscipline) {
        Discipline discipline = discipline(requestedDiscipline);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate maxDate = today.plusDays(BookingWindowPolicy.MAXIMUM_ADVANCE_DAYS);
        if (date.isBefore(today) || date.isAfter(maxDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "OUTSIDE_CALENDAR_HORIZON",
                    "El calendario muestra 7 días contando hoy, hasta hoy + 6 días inclusive.", Map.of("maxDate", maxDate));
        }
        var space = spaces.get(spaceId);
        var start = date.atStartOfDay(ZONE).toInstant();
        var end = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        var occupancy = overlapping(spaceId, start, end);
        var slots = policy.catalog(date, space.openingTime(), space.closingTime(), ZONE, clock.instant(),
                Duration.ofMinutes(settings.bookingToleranceMinutes()), space.status() == SpaceStatus.ACTIVE,
                occupancy.occupied(), occupancy.blocked()).stream().map(mapper::toSlot).toList();
        return new Calendar(spaceId, date, discipline, slots);
    }

    public record Occupancy(List<Interval> occupied, List<Block> blocked) { }

    @Transactional(readOnly = true)
    public Occupancy overlapping(UUID spaceId, Instant start, Instant end) {
        var occupied = reservations.overlappingOccupancy(spaceId, start, end).stream()
                .map(item -> new Interval(item.startAt(), item.endAt())).toList();
        var blocked = blocks.overlapping(spaceId, start, end).stream()
                .map(item -> new Block(new Interval(item.startAt(), item.endAt()), item.reason())).toList();
        return new Occupancy(occupied, blocked);
    }

    public void requireSpace(UUID spaceId) {
        spaces.get(spaceId);
    }

    private Discipline discipline(String value) {
        if (value == null) return null;
        try {
            return Discipline.valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La disciplina indicada no es válida.", Map.of("fields", Map.of("discipline", "valor inválido")));
        }
    }
}
