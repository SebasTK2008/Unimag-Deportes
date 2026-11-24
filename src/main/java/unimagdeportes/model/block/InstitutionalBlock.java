package unimagdeportes.model.block;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "institutional_block")
public class InstitutionalBlock {
    @Id
    private UUID id;
    private UUID spaceId;
    @Enumerated(EnumType.STRING)
    private BlockType type;
    private String reason;
    private Instant startAt;
    private Instant endAt;
    private UUID recurrenceGroup;

    protected InstitutionalBlock() { }

    public InstitutionalBlock(UUID id, UUID spaceId, BlockType type, String reason, Instant startAt,
            Instant endAt, UUID recurrenceGroup) {
        this.id = id;
        this.spaceId = spaceId;
        this.type = type;
        this.reason = reason;
        this.startAt = startAt;
        this.endAt = endAt;
        this.recurrenceGroup = recurrenceGroup;
    }

    public UUID getId() { return id; }
    public UUID getSpaceId() { return spaceId; }
    public BlockType getType() { return type; }
    public String getReason() { return reason; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public UUID getRecurrenceGroup() { return recurrenceGroup; }
}
