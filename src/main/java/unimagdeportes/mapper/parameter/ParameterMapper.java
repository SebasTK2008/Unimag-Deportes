package unimagdeportes.mapper.parameter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import unimagdeportes.config.UsageRuleSettings;
import unimagdeportes.dto.parameter.ParameterDtos.Cancellation;
import unimagdeportes.dto.parameter.ParameterDtos.CheckIn;
import unimagdeportes.dto.parameter.ParameterDtos.Quota;
import unimagdeportes.dto.parameter.ParameterDtos.Reminder;
import unimagdeportes.dto.parameter.ParameterDtos.ReservationRules;
import unimagdeportes.dto.parameter.ParameterDtos.Rules;
import unimagdeportes.dto.parameter.ParameterDtos.Sanctions;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.service.parameter.ParameterService.CurrentParameters;

@Mapper(config = MappingConfig.class)
public interface ParameterMapper {
    @Mapping(target = "weeklyHours", source = "weeklyQuotaHours")
    @Mapping(target = "weekStartsOn", constant = "MONDAY")
    @Mapping(target = "timeZone", constant = "America/Bogota")
    Quota quota(CurrentParameters parameters);

    @Mapping(target = "noShowsForSuspension", source = "parameters.noShowThreshold")
    @Mapping(target = "window", constant = "CALENDAR_MONTH")
    @Mapping(target = "suspensionDays", source = "settings.suspensionDays")
    Sanctions sanctions(CurrentParameters parameters, UsageRuleSettings settings);

    default Rules rules(CurrentParameters parameters, UsageRuleSettings settings) {
        return new Rules(new ReservationRules(60, 2, 7, settings.minAdvanceMinutes(), settings.bookingToleranceMinutes()),
                quota(parameters), new Cancellation(settings.lateCancellationMinutes()),
                new CheckIn(settings.checkinOpeningMinutes(), true), new Reminder(2),
                sanctions(parameters, settings), "@unimagdalena.edu.co");
    }
}
