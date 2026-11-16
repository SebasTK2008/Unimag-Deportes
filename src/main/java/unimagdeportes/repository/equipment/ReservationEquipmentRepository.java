package unimagdeportes.repository.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import unimagdeportes.model.equipment.ReservationEquipment;
import unimagdeportes.model.equipment.ReservationEquipmentId;

public interface ReservationEquipmentRepository extends JpaRepository<ReservationEquipment, ReservationEquipmentId> { }
