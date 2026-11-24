package unimagdeportes.mapper.block;

import org.mapstruct.Mapper;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.model.block.InstitutionalBlock;
import unimagdeportes.service.block.BlockService.BlockInfo;

@Mapper(config = MappingConfig.class)
public interface BlockMapper {
    BlockInfo toInfo(InstitutionalBlock block);
}
