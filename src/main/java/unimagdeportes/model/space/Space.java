package unimagdeportes.model.space;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
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
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE}, optional = false)
    @JoinColumn(name = "preview_image_id", nullable = false, unique = true)
    private SpaceImage previewImage;
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE}, optional = false)
    @JoinColumn(name = "panorama_image_id", nullable = false, unique = true)
    private SpaceImage panoramaImage;
    @ElementCollection
    @CollectionTable(name = "space_discipline", joinColumns = @JoinColumn(name = "space_id"))
    @Column(name = "discipline")
    @Enumerated(EnumType.STRING)
    private Set<Discipline> disciplines = new HashSet<>();

    protected Space() { }

    public Space(UUID id, String name, int capacity, String requirements, LocalTime openingTime,
            LocalTime closingTime, SpaceStatus status, Set<Discipline> disciplines,
            SpaceImage previewImage, SpaceImage panoramaImage) {
        this.id = id;
        this.previewImage = java.util.Objects.requireNonNull(previewImage);
        this.panoramaImage = java.util.Objects.requireNonNull(panoramaImage);
        update(name, capacity, requirements, openingTime, closingTime, status, disciplines);
    }

    public void update(String name, int capacity, String requirements, LocalTime openingTime,
            LocalTime closingTime, SpaceStatus status, Set<Discipline> disciplines) {
        this.name = name;
        this.capacity = capacity;
        this.requirements = requirements;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.status = status;
        this.disciplines.clear();
        this.disciplines.addAll(disciplines);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public String getRequirements() { return requirements; }
    public LocalTime getOpeningTime() { return openingTime; }
    public LocalTime getClosingTime() { return closingTime; }
    public SpaceStatus getStatus() { return status; }
    public Set<Discipline> getDisciplines() { return Set.copyOf(disciplines); }
    public SpaceImage getPreviewImage() { return previewImage; }
    public SpaceImage getPanoramaImage() { return panoramaImage; }
}
