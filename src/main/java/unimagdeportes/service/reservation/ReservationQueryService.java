package unimagdeportes.service.reservation;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.dto.reservation.ReservationDtos;
import unimagdeportes.mapper.reservation.ReservationMapper;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.repository.reservation.ReservationRepository;
import unimagdeportes.service.equipment.EquipmentService;
import unimagdeportes.service.space.SpaceService;

/**
 * Consulta de "Mis reservas": lista las reservas nominales del usuario autenticado,
 * próximas y pasadas, en todos los estados (RN-03, RN-05).
 */
@Service
public class ReservationQueryService {
    private final ReservationRepository reservations;
    private final SpaceService spaces;
    private final EquipmentService equipment;
    private final ReservationMapper mapper;

    public ReservationQueryService(ReservationRepository reservations, SpaceService spaces,
            EquipmentService equipment, ReservationMapper mapper) {
        this.reservations = reservations;
        this.spaces = spaces;
        this.equipment = equipment;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ReservationDtos.Response> listMine(UUID userId) {
        return reservations.findByUserIdOrderByStartAtDesc(userId).stream()
                .map(this::toResponse).toList();
    }

    private ReservationDtos.Response toResponse(Reservation reservation) {
        var space = spaces.get(reservation.getSpaceId());
        return mapper.toResponse(reservation, space.name(), equipment.forReservation(reservation.getId()));
    }
}
