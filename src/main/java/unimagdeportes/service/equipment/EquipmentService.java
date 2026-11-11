package unimagdeportes.service.equipment;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.equipment.EquipmentStatus;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.repository.equipment.EquipmentRepository;

@Service
public class EquipmentService {
    private final EquipmentRepository equipment;

    public EquipmentService(EquipmentRepository equipment) { this.equipment = equipment; }

    @Transactional(readOnly = true)
    public EquipmentInfo get(UUID id) {
        var item = equipment.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "El implemento no existe."));
        return new EquipmentInfo(item.getId(), item.getName(), item.getDiscipline(), item.getStock(), item.getStatus());
    }

    public record EquipmentInfo(UUID id, String name, Discipline discipline, int stock, EquipmentStatus status) { }
}
