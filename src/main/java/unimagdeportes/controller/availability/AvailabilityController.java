package unimagdeportes.controller.availability;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import unimagdeportes.dto.availability.AvailabilityDtos.Calendar;
import unimagdeportes.dto.availability.AvailabilityDtos.SpaceList;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.service.availability.AvailabilityService;
import unimagdeportes.service.availability.AvailabilityStream;

@RestController
@RequestMapping("/spaces")
public class AvailabilityController {
    private final AvailabilityService availability;
    private final AvailabilityStream stream;

    public AvailabilityController(AvailabilityService availability, AvailabilityStream stream) {
        this.availability = availability;
        this.stream = stream;
    }

    @GetMapping
    SpaceList spaces(@RequestParam(required = false) String discipline) {
        return availability.spaces(discipline);
    }

    @GetMapping("/{id}/availability")
    Calendar calendar(@PathVariable UUID id, @RequestParam String date,
            @RequestParam(required = false) String discipline) {
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date);
        } catch (DateTimeParseException error) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "La fecha indicada no es válida.");
        }
        return availability.calendar(id, parsed, discipline);
    }

    @GetMapping(value = "/{id}/availability/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<SseEmitter> stream(@PathVariable UUID id) {
        availability.requireSpace(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(stream.subscribe(id));
    }

}
