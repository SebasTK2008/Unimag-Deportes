package unimagdeportes.service.block;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import unimagdeportes.dto.block.BlockDtos.CreateBlock;
import unimagdeportes.exception.BusinessException;

@Component
public class BlockSchedule {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final Duration SLOT = Duration.ofHours(1);

    public List<Slot> materialize(CreateBlock request) {
        if (request == null || request.spaceId() == null || request.type() == null
                || request.reason() == null || request.reason().isBlank()
                || request.startAt() == null || request.endAt() == null
                || !request.endAt().isAfter(request.startAt())) {
            throw invalid();
        }
        Duration duration = Duration.between(request.startAt(), request.endAt());
        if (duration.getNano() != 0 || duration.getSeconds() % SLOT.getSeconds() != 0) throw invalid();
        var recurrence = request.recurrence();
        if (recurrence == null) return slots(request.startAt(), duration);
        LocalDate startDate = request.startAt().atZone(ZONE).toLocalDate();
        if (recurrence.until() == null || recurrence.until().isBefore(startDate)
                || recurrence.daysOfWeek() == null || recurrence.daysOfWeek().isEmpty()
                || recurrence.daysOfWeek().stream().anyMatch(java.util.Objects::isNull)) throw invalid();
        var startTime = request.startAt().atZone(ZONE).toLocalTime();
        List<Slot> result = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(recurrence.until()); date = date.plusDays(1)) {
            if (recurrence.daysOfWeek().contains(date.getDayOfWeek())) {
                result.addAll(slots(date.atTime(startTime).atZone(ZONE).toInstant(), duration));
            }
        }
        if (result.isEmpty()) throw invalid();
        result.sort(java.util.Comparator.comparing(Slot::start));
        return result;
    }

    private List<Slot> slots(Instant start, Duration duration) {
        List<Slot> result = new ArrayList<>();
        Instant end = start.plus(duration);
        for (Instant cursor = start; cursor.isBefore(end); cursor = cursor.plus(SLOT)) {
            result.add(new Slot(cursor, cursor.plus(SLOT)));
        }
        return result;
    }

    private BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "El bloqueo no es válido.");
    }

    public record Slot(Instant start, Instant end) { }
}
