package unimagdeportes.mapper.space;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import unimagdeportes.dto.space.SpaceDtos.ImageContent;
import unimagdeportes.dto.space.SpaceDtos.SpaceInfo;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.model.space.Space;
import unimagdeportes.model.space.SpaceImage;

@Mapper(config = MappingConfig.class)
public interface SpaceMapper {
    SpaceInfo toInfo(Space space);

    @Mapping(target = "content", expression = "java(image.getContent())")
    ImageContent toImageContent(SpaceImage image);
}
