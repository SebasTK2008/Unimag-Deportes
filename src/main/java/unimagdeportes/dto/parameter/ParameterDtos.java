package unimagdeportes.dto.parameter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class ParameterDtos {
    private ParameterDtos() { }

    public record Rules(ReservationRules reservation, Quota quota, Cancellation cancellation,
            CheckIn checkIn, Reminder reminder, Sanctions sanctions, String emailDomain) { }

    public record ReservationRules(int slotMinutes, int maxSlotsPerReservation, int maxAdvanceDays,
            int minAdvanceMinutes, int bookingToleranceMinutes) { }
    public record Quota(int weeklyHours, String weekStartsOn, String timeZone) { }
    public record Cancellation(int lateThresholdMinutes) { }
    public record CheckIn(int opensMinutesBefore, boolean closesAtReservationEnd) { }
    public record Reminder(int hoursBefore) { }
    public record Sanctions(int noShowsForSuspension, String window, int suspensionDays) { }

    public record UpdateParameters(@Valid QuotaUpdate quota, @Valid SanctionsUpdate sanctions) { }
    public record QuotaUpdate(
            @Positive(message = "La cuota semanal debe ser mayor que cero.")
            @JsonDeserialize(using = ParameterIntegerDeserializer.class) Integer weeklyHours) { }
    public record SanctionsUpdate(
            @Positive(message = "El umbral de inasistencias debe ser mayor que cero.")
            @JsonDeserialize(using = ParameterIntegerDeserializer.class) Integer noShowsForSuspension) { }
}
