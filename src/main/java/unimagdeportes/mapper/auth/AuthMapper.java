package unimagdeportes.mapper.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import unimagdeportes.dto.auth.AuthDtos.Profile;
import unimagdeportes.mapper.MappingConfig;
import unimagdeportes.model.user.AppUser;

@Mapper(config = MappingConfig.class)
public interface AuthMapper {
    @Mapping(target = "needsName", expression = "java(needsName(user))")
    @Mapping(target = "suspendedUntil", expression = "java(activeSuspension(user.getSuspendedUntil(), clock))")
    Profile profile(AppUser user, @Context Clock clock);

    default boolean needsName(AppUser user) {
        return user.getName() == null || user.getName().isBlank();
    }

    default OffsetDateTime offset(Instant instant) {
        return instant.atZone(ZoneId.of("America/Bogota")).toOffsetDateTime();
    }

    default OffsetDateTime activeSuspension(Instant until, Clock clock) {
        return until != null && clock.instant().isBefore(until) ? offset(until) : null;
    }
}
