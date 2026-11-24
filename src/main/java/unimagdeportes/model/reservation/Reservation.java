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

    public Reservation(UUID id, UUID userId, UUID spaceId, Instant startAt, Instant endAt, Instant createdAt,
            int cancellationThresholdMinutes, int checkinOpeningMinutes) {
        this.id = id;
        this.userId = userId;
        this.spaceId = spaceId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = createdAt;
        this.status = ReservationStatus.CONFIRMED;
        this.cancellationThresholdMinutes = cancellationThresholdMinutes;
        this.checkinOpeningMinutes = checkinOpeningMinutes;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getSpaceId() { return spaceId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public int getCancellationThresholdMinutes() { return cancellationThresholdMinutes; }
    public int getCheckinOpeningMinutes() { return checkinOpeningMinutes; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public void checkIn(Instant now) { this.checkedInAt = now; this.status = ReservationStatus.IN_USE; }
    public void markReminderSent(Instant sentAt) { this.reminderSentAt = sentAt; }
    public void cancelByAdministrator() { this.status = ReservationStatus.CANCELLED_BY_ADMIN; }
    public void cancelOnTime() { this.status = ReservationStatus.CANCELLED_ON_TIME; }
    public void cancelLate() { this.status = ReservationStatus.CANCELLED_LATE; }
    public void cancelBySuspension() { this.status = ReservationStatus.CANCELLED_BY_SUSPENSION; }
    public void markNoShow() { this.status = ReservationStatus.NO_SHOW; }
    public void complete() { this.status = ReservationStatus.COMPLETED; }
}
