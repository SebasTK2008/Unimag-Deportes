package unimagdeportes.service.report;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.dto.availability.AvailabilityDtos.SlotStatus;
import unimagdeportes.dto.report.ReportDtos.Occupancy;
import unimagdeportes.dto.report.ReportDtos.SpaceOccupancy;
import unimagdeportes.dto.space.SpaceDtos.SpaceInfo;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mapper.report.ReportMapper;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.model.space.SpaceStatus;
import unimagdeportes.service.block.BlockService;
import unimagdeportes.service.policy.SlotCatalogPolicy;
import unimagdeportes.service.policy.SlotCatalogPolicy.Block;
import unimagdeportes.service.policy.SlotCatalogPolicy.Interval;
import unimagdeportes.service.policy.SlotCatalogPolicy.SlotDecision;
import unimagdeportes.service.reservation.ReservationService;
import unimagdeportes.service.reservation.ReservationService.ReservationInfo;
import unimagdeportes.service.space.SpaceService;

/**
 * Ocupación por espacio y franja para el panel del Administrador (UC09-H1). Solo lectura.
 * Reutiliza la derivación de franjas del calendario (UC02): la reserva ocupa el espacio
 * completo, sin importar la disciplina (RN-07).
 */
@Service
public class OccupancyService {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final SpaceService spaces;
    private final ReservationService reservations;
    private final BlockService blocks;
    private final ReportMapper mapper;
    private final Clock clock;
    private final SlotCatalogPolicy policy = new SlotCatalogPolicy();

    public OccupancyService(SpaceService spaces, ReservationService reservations, BlockService blocks,
            ReportMapper mapper, Clock clock) {
        this.spaces = spaces;
        this.reservations = reservations;
        this.blocks = blocks;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Occupancy occupancy(LocalDate date, String requestedDiscipline, UUID spaceId) {
        Discipline discipline = discipline(requestedDiscipline);
        var result = spaces.list().stream()
                .filter(space -> spaceId == null || space.id().equals(spaceId))
                .filter(space -> discipline == null || space.disciplines().contains(discipline))
                .sorted(Comparator.comparing(SpaceInfo::name).thenComparing(SpaceInfo::id))
                .map(space -> spaceOccupancy(space, date))
                .toList();
        return new Occupancy(date, result);
    }

    private SpaceOccupancy spaceOccupancy(SpaceInfo space, LocalDate date) {
        Instant dayStart = date.atStartOfDay(ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        var dayReservations = reservations.overlappingOccupancy(space.id(), dayStart, dayEnd);
        var occupied = dayReservations.stream().map(item -> new Interval(item.startAt(), item.endAt())).toList();
        var blocked = blocks.overlapping(space.id(), dayStart, dayEnd).stream()
                .map(item -> new Block(new Interval(item.startAt(), item.endAt()), item.reason())).toList();
        // La tolerancia solo influye en "bookable", que el panel no expone.
        var slots = policy.catalog(date, space.openingTime(), space.closingTime(), ZONE, clock.instant(),
                Duration.ZERO, space.status() == SpaceStatus.ACTIVE, occupied, blocked).stream()
                .map(slot -> mapper.toSlot(slot, occupant(slot, dayReservations)))
                .toList();
        return new SpaceOccupancy(space.id(), space.name(), slots);
    }

    private UUID occupant(SlotDecision slot, List<ReservationInfo> dayReservations) {
        if (slot.status() != SlotStatus.OCCUPIED) return null;
        return dayReservations.stream()
                .filter(item -> new Interval(item.startAt(), item.endAt()).overlaps(slot.startAt(), slot.endAt()))
                .map(ReservationInfo::id)
                .findFirst().orElse(null);
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
