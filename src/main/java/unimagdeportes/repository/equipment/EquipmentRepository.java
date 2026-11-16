package unimagdeportes.repository.equipment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import unimagdeportes.model.equipment.Equipment;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id")
    Optional<Equipment> findForUpdate(UUID id);
}
