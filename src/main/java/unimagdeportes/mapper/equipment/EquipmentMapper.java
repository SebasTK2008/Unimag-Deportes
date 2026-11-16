package unimagdeportes.mapper.equipment;

import org.mapstruct.Mapper;
import unimagdeportes.dto.equipment.EquipmentDtos.Info;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.model.equipment.Equipment;

@Mapper(config = MappingConfig.class)
public interface EquipmentMapper {
    Info toInfo(Equipment equipment);
}
