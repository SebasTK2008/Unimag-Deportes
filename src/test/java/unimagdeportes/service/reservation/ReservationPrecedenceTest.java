package unimagdeportes.service.reservation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import unimagdeportes.config.UsageRuleSettings;
import unimagdeportes.dto.space.SpaceDtos.SpaceInfo;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.model.space.SpaceStatus;
import unimagdeportes.model.user.AppUser;
import unimagdeportes.repository.reservation.ReservationRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.availability.AvailabilityService;
import unimagdeportes.service.availability.AvailabilityService.Occupancy;
import unimagdeportes.service.equipment.EquipmentService;
import unimagdeportes.service.parameter.ParameterService;
import unimagdeportes.service.policy.SlotCatalogPolicy.Block;
import unimagdeportes.service.policy.SlotCatalogPolicy.Interval;
import unimagdeportes.service.quota.QuotaService;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.user.UserService;

class ReservationPrecedenceTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID spaceId = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-11-11T13:00:00Z");
    private final Instant end = start.plusSeconds(3600);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final UserService users = mock(UserService.class);
    private final SpaceService spaces = mock(SpaceService.class);
    private final QuotaService quota = mock(QuotaService.class);
    private final ParameterService parameters = mock(ParameterService.class);
    private final ReservationCreationService service = new ReservationCreationService(reservations,
            mock(ReservationLock.class), users, spaces, availability, mock(EquipmentService.class),
            parameters, quota, mock(AuditService.class),
            mock(ApplicationEventPublisher.class), new UsageRuleSettings(10, 60, 0, 10, 7),
            Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneId.of("America/Bogota")), "");

    @BeforeEach
    void setUp() {
        var user = mock(AppUser.class);
        when(user.getEmail()).thenReturn("user@unimagdalena.edu.co");
        when(user.getName()).thenReturn("Usuario");
        when(users.findForUpdate(userId)).thenReturn(user);
        when(spaces.getForUpdate(spaceId)).thenReturn(new SpaceInfo(spaceId, "Cancha", 10, null,
                LocalTime.of(7, 0), LocalTime.of(11, 0), SpaceStatus.ACTIVE, Set.of(Discipline.TENNIS)));
    }

    @Test
    void institutionalBlockTakesPrecedenceOverOccupiedSpace() {
        var interval = new Interval(start, end);
        when(availability.overlapping(spaceId, start, end))
                .thenReturn(new Occupancy(List.of(interval), List.of(new Block(interval, "Clase"))));

        assertRejection("BLOCKED_SLOT");
    }

    @Test
    void occupiedSpaceTakesPrecedenceOverTheUsersOwnOverlap() {
        when(availability.overlapping(spaceId, start, end))
                .thenReturn(new Occupancy(List.of(new Interval(start, end)), List.of()));
        when(reservations.findByUserIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                eq(userId), any(), eq(end), eq(start))).thenReturn(List.of(mock(Reservation.class)));

        assertRejection("SLOT_TAKEN");
    }

    @Test
    void ownOverlapIsReportedWhenTheRequestedSpaceIsFree() {
        when(availability.overlapping(spaceId, start, end)).thenReturn(new Occupancy(List.of(), List.of()));
        when(reservations.findByUserIdAndStatusInAndStartAtLessThanAndEndAtGreaterThan(
                eq(userId), any(), eq(end), eq(start))).thenReturn(List.of(mock(Reservation.class)));

        assertRejection("OVERLAPPING_RESERVATION");
    }

    @Test
    void dailyLimitRejectsAThirdHourBeforeCheckingWeeklyQuota() {
        when(availability.overlapping(spaceId, start, end)).thenReturn(new Occupancy(List.of(), List.of()));
        when(quota.usedOnDate(userId, start.atZone(ZoneId.of("America/Bogota")).toLocalDate()))
                .thenReturn(Duration.ofHours(2));
        assertRejection("DAILY_QUOTA_EXCEEDED");
        verify(parameters, never()).current();
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void exactlyTwoDailyHoursAreAcceptedButWeeklyQuotaIsStillEnforced() {
        when(availability.overlapping(spaceId, start, end)).thenReturn(new Occupancy(List.of(), List.of()));
        when(quota.usedOnDate(userId, start.atZone(ZoneId.of("America/Bogota")).toLocalDate()))
                .thenReturn(Duration.ofHours(1));
        when(parameters.current()).thenReturn(new ParameterService.CurrentParameters(1, 3));
        when(quota.usedInWeek(eq(userId), any(), any())).thenReturn(Duration.ofHours(1));
        assertRejection("QUOTA_EXCEEDED");
    }

    private void assertRejection(String code) {
        assertThatThrownBy(() -> service.create(userId, spaceId, start, 1, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode()).isEqualTo(code);
    }
}
