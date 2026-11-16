package unimagdeportes.controller.reservation;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.dto.reservation.ReservationDtos;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.security.UserPrincipal;
import unimagdeportes.service.equipment.EquipmentService.RequestedEquipment;
import unimagdeportes.service.reservation.ReservationCreationService;

@RestController
public class ReservationController {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final ReservationCreationService reservations;

    public ReservationController(ReservationCreationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    ReservationDtos.Response create(@RequestBody ReservationDtos.Request request,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (request.spaceId() == null) fields.put("spaceId", "es obligatorio");
        if (request.startAt() == null) fields.put("startAt", "es obligatorio");
        if (request.slots() == null || request.slots() < 1 || request.slots() > 2) {
            fields.put("slots", "debe ser 1 o 2");
        }
        if (!fields.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La solicitud contiene campos inválidos.", Map.of("fields", fields));
        }
        var requested = request.equipment() == null ? List.<ReservationDtos.EquipmentRequest>of() : request.equipment();
        var ids = new HashSet<java.util.UUID>();
        for (int index = 0; index < requested.size(); index++) {
            var item = requested.get(index);
            if (item == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "La solicitud contiene implementos inválidos.", Map.of("fields",
                                Map.of("equipment[" + index + "]", "es obligatorio")));
            }
            if (item.equipmentId() == null || !ids.add(item.equipmentId())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "La solicitud contiene implementos inválidos o repetidos.", Map.of("fields",
                                Map.of("equipment[" + index + "].equipmentId", "es obligatorio y no debe repetirse")));
            }
            if (item.quantity() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "La solicitud contiene implementos inválidos.", Map.of("fields",
                                Map.of("equipment[" + index + "].quantity", "es obligatorio")));
            }
            if (item.quantity() <= 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY",
                        "La cantidad debe ser mayor que cero.", Map.of("equipmentId", item.equipmentId()));
            }
        }
        var result = reservations.create(principal.id(), request.spaceId(), request.startAt().toInstant(), request.slots(),
                requested.stream().map(item -> new RequestedEquipment(item.equipmentId(), item.quantity())).toList());
        return new ReservationDtos.Response(result.id(), new ReservationDtos.Space(result.spaceId(), result.spaceName()),
                result.startAt().atZone(ZONE).toOffsetDateTime(), result.endAt().atZone(ZONE).toOffsetDateTime(),
                result.status(), result.createdAt().atZone(ZONE).toOffsetDateTime(), null,
                result.equipment().stream().map(item -> new ReservationDtos.Equipment(
                        item.equipmentId(), item.name(), item.quantity())).toList());
    }
}
