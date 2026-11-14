package unimagdeportes.repository.reservation;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unimagdeportes.model.reservation.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Query(value = "select id from reservation where status = 'CONFIRMED' and reminder_sent_at is null "
            + "and created_at <= start_at - interval '2 hours' and start_at - interval '2 hours' <= :now "
            + "and start_at > :now", nativeQuery = true)
    List<UUID> findDueReminders(@Param("now") Instant now);
}
