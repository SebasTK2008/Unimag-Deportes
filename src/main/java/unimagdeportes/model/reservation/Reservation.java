package unimagdeportes.model.reservation;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation")
public class Reservation {
    @Id
    private UUID id;
    private UUID userId;
    private UUID spaceId;
    private Instant startAt;
    private Instant endAt;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    private Instant createdAt;
    private Instant checkedInAt;
    private Instant reminderSentAt;
    private int cancellationThresholdMinutes;
    private int checkinOpeningMinutes;

    protected Reservation() { }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getSpaceId() { return spaceId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public ReservationStatus getStatus() { return status; }
}
