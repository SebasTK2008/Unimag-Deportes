package unimagdeportes.repository.reservation;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.model.reservation.ReservationStatus;
import java.util.Collection;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Query(value = "select id from reservation where status = 'CONFIRMED' and reminder_sent_at is null "
            + "and created_at <= start_at - interval '2 hours' and start_at - interval '2 hours' <= :now "
            + "and start_at > :now", nativeQuery = true)
    List<UUID> findDueReminders(@Param("now") Instant now);

    List<Reservation> findBySpaceIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
            UUID spaceId, Collection<ReservationStatus> statuses, Instant end, Instant start);

    List<Reservation> findByUserIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
            UUID userId, Collection<ReservationStatus> statuses, Instant end, Instant start);

    List<Reservation> findBySpaceIdAndUserIdOrderByStartAtAsc(UUID spaceId, UUID userId);

    List<Reservation> findByUserIdOrderByStartAtDesc(UUID userId);

    /** Reservas cuya ventana de check-in cerró sin asistencia; la ventana termina en {@code end_at} (RN-30). */
    @Query(value = "select id from reservation where status = 'CONFIRMED' and checked_in_at is null "
            + "and end_at <= :now order by end_at", nativeQuery = true)
    List<UUID> findClosedWithoutCheckIn(@Param("now") Instant now);

    @Query(value = "select id from reservation where status = 'IN_USE' and end_at <= :now order by end_at",
            nativeQuery = true)
    List<UUID> findFinishedInUse(@Param("now") Instant now);

    long countByUserIdAndStatusAndEndAtGreaterThanEqualAndEndAtLessThan(
            UUID userId, ReservationStatus status, Instant from, Instant to);

    List<Reservation> findByUserIdAndStatusInAndStartAtGreaterThanAndEndAtLessThanEqual(
            UUID userId, Collection<ReservationStatus> statuses, Instant after, Instant until);

    long countByIdInAndStatus(Collection<UUID> ids, ReservationStatus status);

    boolean existsBySpaceIdAndUserIdNotAndStatusInAndStartAtLessThanEqualAndEndAtGreaterThan(
            UUID spaceId, UUID userId, Collection<ReservationStatus> statuses, Instant now, Instant end);
}
