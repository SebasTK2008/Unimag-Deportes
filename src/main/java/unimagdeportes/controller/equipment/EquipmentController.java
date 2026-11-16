package unimagdeportes.controller.equipment;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.dto.equipment.EquipmentDtos;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.service.equipment.EquipmentService;

@RestController
public class EquipmentController {
    private final EquipmentService equipment;

    public EquipmentController(EquipmentService equipment) {
        this.equipment = equipment;
    }

    @GetMapping("/equipment")
    EquipmentDtos.Catalog list(@RequestParam(required = false) Discipline discipline) {
        return new EquipmentDtos.Catalog(equipment.list(discipline));
    }

    @GetMapping("/equipment/availability")
    EquipmentDtos.Availability availability(@RequestParam OffsetDateTime startAt, @RequestParam OffsetDateTime endAt,
            @RequestParam(required = false) Discipline discipline) {
        return equipment.availability(startAt, endAt, discipline);
    }

    @PostMapping("/admin/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    EquipmentDtos.Info create(@RequestBody EquipmentDtos.Input input) {
        return equipment.create(input);
    }

    @PutMapping("/admin/equipment/{id}")
    EquipmentDtos.Info update(@PathVariable UUID id, @RequestBody EquipmentDtos.Update input) {
        return equipment.update(id, input);
    }
}
