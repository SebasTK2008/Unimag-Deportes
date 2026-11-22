package unimagdeportes.mapper.space;

import org.mapstruct.Mapper;
import unimagdeportes.dto.space.SpaceDtos.ImageReference;
import unimagdeportes.dto.space.SpaceDtos.SpaceInfo;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.model.space.Space;
import unimagdeportes.model.space.SpaceImage;

@Mapper(config = MappingConfig.class)
public interface SpaceMapper {
    SpaceInfo toInfo(Space space);

    ImageReference toImageReference(SpaceImage image);
}
