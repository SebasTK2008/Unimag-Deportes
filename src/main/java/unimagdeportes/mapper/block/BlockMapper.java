package unimagdeportes.mapper.block;

import org.mapstruct.Mapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import unimagdeportes.dto.block.BlockDtos.AdminBlock;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.model.block.InstitutionalBlock;
import unimagdeportes.service.block.BlockService.BlockInfo;

@Mapper(config = MappingConfig.class)
public interface BlockMapper {
    BlockInfo toInfo(InstitutionalBlock block);

    AdminBlock toAdminBlock(InstitutionalBlock block);

    default OffsetDateTime toBogota(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneId.of("America/Bogota")).toOffsetDateTime();
    }
}
