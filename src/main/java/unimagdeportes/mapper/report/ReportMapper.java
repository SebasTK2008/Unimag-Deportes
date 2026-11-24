package unimagdeportes.mapper.report;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import unimagdeportes.dto.report.ReportDtos.Indicators;
import unimagdeportes.dto.report.ReportDtos.OccupancySlot;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.service.policy.SlotCatalogPolicy.SlotDecision;
import unimagdeportes.service.report.IndicatorService.IndicatorSnapshot;

@Mapper(config = MappingConfig.class)
public interface ReportMapper {
    @Mapping(target = "startAt", source = "decision.startAt")
    @Mapping(target = "endAt", source = "decision.endAt")
    @Mapping(target = "status", source = "decision.status")
    @Mapping(target = "reservationId", source = "reservationId")
    OccupancySlot toSlot(SlotDecision decision, UUID reservationId);

    Indicators toIndicators(IndicatorSnapshot snapshot);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(ZoneId.of("America/Bogota")).toOffsetDateTime();
    }
}
