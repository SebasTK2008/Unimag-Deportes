package unimagdeportes.model.parameter;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "usage_parameters")
@Immutable
public class UsageParameters {
    @Id
    private UUID id;
    private long versionNumber;
    private int weeklyQuotaHours;
    private int noShowThreshold;
    private Instant createdAt;
    private UUID createdBy;

    protected UsageParameters() { }

    private UsageParameters(long versionNumber, int weeklyQuotaHours, int noShowThreshold,
            Instant createdAt, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.versionNumber = versionNumber;
        this.weeklyQuotaHours = weeklyQuotaHours;
        this.noShowThreshold = noShowThreshold;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public UUID getId() { return id; }
    public long getVersionNumber() { return versionNumber; }
    public int getWeeklyQuotaHours() { return weeklyQuotaHours; }
    public int getNoShowThreshold() { return noShowThreshold; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }

    public UsageParameters nextVersion(int weeklyQuotaHours, int noShowThreshold, Instant createdAt, UUID createdBy) {
        return new UsageParameters(Math.incrementExact(versionNumber), weeklyQuotaHours, noShowThreshold,
                createdAt, createdBy);
    }
}
