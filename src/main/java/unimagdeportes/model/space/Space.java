package unimagdeportes.model.space;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "space")
public class Space {
    @Id
    private UUID id;
    private String name;
    private int capacity;
    private String requirements;
    private LocalTime openingTime;
    private LocalTime closingTime;
    @Enumerated(EnumType.STRING)
    private SpaceStatus status;
    @ElementCollection
    @CollectionTable(name = "space_discipline", joinColumns = @JoinColumn(name = "space_id"))
    @Column(name = "discipline")
    @Enumerated(EnumType.STRING)
    private Set<Discipline> disciplines = new HashSet<>();

    protected Space() { }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public String getRequirements() { return requirements; }
    public LocalTime getOpeningTime() { return openingTime; }
    public LocalTime getClosingTime() { return closingTime; }
    public SpaceStatus getStatus() { return status; }
    public Set<Discipline> getDisciplines() { return Set.copyOf(disciplines); }
}
