package unimagdeportes.repository.equipment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import unimagdeportes.model.equipment.Equipment;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> { }
