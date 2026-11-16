package unimagdeportes.model.equipment;

import java.io.Serializable;
import java.util.UUID;

public record ReservationEquipmentId(UUID reservationId, UUID equipmentId) implements Serializable { }
