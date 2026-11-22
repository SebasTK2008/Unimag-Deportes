package unimagdeportes.controller.report;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.dto.report.ReportDtos.Indicators;
import unimagdeportes.dto.report.ReportDtos.Occupancy;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.service.report.IndicatorService;
import unimagdeportes.service.report.OccupancyService;

/** Panel del Administrador (UC09); el rol lo exige la regla {@code /admin/**} de {@code SecurityConfig}. */
@RestController
@RequestMapping("/admin")
public class ReportController {
    private final OccupancyService occupancy;
    private final IndicatorService indicators;

    public ReportController(OccupancyService occupancy, IndicatorService indicators) {
        this.occupancy = occupancy;
        this.indicators = indicators;
    }

    @GetMapping("/occupancy")
    Occupancy occupancy(@RequestParam(required = false) String date,
            @RequestParam(required = false) String discipline, @RequestParam(required = false) UUID spaceId) {
        if (date == null || date.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La fecha es obligatoria.",
                    Map.of("fields", Map.of("date", "valor inválido")));
        }
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date);
        } catch (DateTimeParseException error) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "La fecha indicada no es válida.");
        }
        return occupancy.occupancy(parsed, discipline, spaceId);
    }

    @GetMapping("/indicators")
    Indicators indicators(@RequestParam String period) {
        return indicators.indicators(period);
    }
}
