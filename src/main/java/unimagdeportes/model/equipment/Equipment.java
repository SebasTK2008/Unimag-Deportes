package unimagdeportes.model.equipment;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import unimagdeportes.model.space.Discipline;

@Entity
@Table(name = "equipment")
public class Equipment {
    @Id
    private UUID id;
    private String name;
    @Enumerated(EnumType.STRING)
    private Discipline discipline;
    private int stock;
    @Enumerated(EnumType.STRING)
    private EquipmentStatus status;

    protected Equipment() { }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Discipline getDiscipline() { return discipline; }
    public int getStock() { return stock; }
    public EquipmentStatus getStatus() { return status; }
}
