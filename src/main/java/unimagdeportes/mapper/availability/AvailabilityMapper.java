package unimagdeportes.mapper.availability;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import unimagdeportes.dto.availability.AvailabilityDtos.Slot;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.service.policy.SlotCatalogPolicy.SlotDecision;

@Mapper(config = MappingConfig.class)
public interface AvailabilityMapper {
    Slot toSlot(SlotDecision decision);

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(ZoneId.of("America/Bogota")).toOffsetDateTime();
    }
}
