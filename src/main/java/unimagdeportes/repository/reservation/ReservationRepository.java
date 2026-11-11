package unimagdeportes.repository.reservation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import unimagdeportes.model.reservation.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> { }
