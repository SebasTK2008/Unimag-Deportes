package unimagdeportes.mapper.block;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import unimagdeportes.dto.block.BlockDtos.AdminBlock;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.model.block.InstitutionalBlock;
import unimagdeportes.service.block.BlockService.BlockInfo;

@Mapper(config = MappingConfig.class)
public interface BlockMapper {
    BlockInfo toInfo(InstitutionalBlock block);

    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "createdByName", source = "createdByName")
    @Mapping(target = "cancelledByName", source = "cancelledByName")
    AdminBlock toAdminBlock(InstitutionalBlock block, UUID createdBy, String createdByName, String cancelledByName);

    default OffsetDateTime toBogota(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneId.of("America/Bogota")).toOffsetDateTime();
    }
}
