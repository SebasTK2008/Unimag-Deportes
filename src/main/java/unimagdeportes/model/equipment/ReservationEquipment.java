package unimagdeportes.model.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "reservation_equipment")
@IdClass(ReservationEquipmentId.class)
public class ReservationEquipment {
    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;
    @Id
    @Column(name = "equipment_id")
    private UUID equipmentId;
    private int quantity;

    protected ReservationEquipment() { }

    public ReservationEquipment(UUID reservationId, UUID equipmentId, int quantity) {
        this.reservationId = reservationId;
        this.equipmentId = equipmentId;
        this.quantity = quantity;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getEquipmentId() { return equipmentId; }
    public int getQuantity() { return quantity; }
}
