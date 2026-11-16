package unimagdeportes.mapper.quota;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import unimagdeportes.dto.quota.QuotaDtos.WeeklyQuota;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.service.quota.QuotaService.WeekSnapshot;

@Mapper(config = MappingConfig.class)
public interface QuotaMapper {
    @Mapping(target = "limitHours", source = "limit")
    @Mapping(target = "usedHours", source = "used")
    @Mapping(target = "availableHours", source = "available")
    WeeklyQuota toWeeklyQuota(WeekSnapshot snapshot);

    /** Las franjas son de 60 minutos alineadas a la hora, por lo que las duraciones son horas enteras. */
    default long toHours(Duration duration) {
        return duration.toHours();
    }

    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(ZoneId.of("America/Bogota")).toOffsetDateTime();
    }
}
