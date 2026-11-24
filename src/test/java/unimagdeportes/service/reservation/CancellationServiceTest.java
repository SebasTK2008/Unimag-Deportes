package unimagdeportes.service.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import unimagdeportes.dto.space.SpaceDtos.SpaceInfo;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.event.ReservationNotificationEvent;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mapper.reservation.ReservationMapper;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.model.space.SpaceStatus;
import unimagdeportes.model.user.AppUser;
import unimagdeportes.model.user.Role;
import unimagdeportes.repository.reservation.ReservationRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.equipment.EquipmentService;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.user.UserService;

class CancellationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ReservationLock lock = mock(ReservationLock.class);
    private final UserService users = mock(UserService.class);
    private final SpaceService spaces = mock(SpaceService.class);
    private final EquipmentService equipment = mock(EquipmentService.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CancellationService service = new CancellationService(reservations, lock,
            Clock.fixed(NOW, ZoneOffset.UTC), users, spaces, equipment, audit, events, new ReservationMapper());
    private final UUID userId = UUID.randomUUID();
    private final UUID spaceId = UUID.randomUUID();

    @Test
    void locksBeforeReadingAndPublishesAvailabilityAndNotification() {
        var reservation = reservation();
        when(reservations.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(spaces.get(spaceId)).thenReturn(new SpaceInfo(spaceId, "Cancha", 10, null, LocalTime.of(7, 0),
                LocalTime.of(11, 0), SpaceStatus.ACTIVE, Set.of()));
        when(users.findById(userId)).thenReturn(new AppUser(userId, "user@unimagdalena.edu.co", Role.USER, NOW));
        when(equipment.forReservation(reservation.getId())).thenReturn(List.of());

        var result = service.cancel(userId, reservation.getId());

        assertThat(result.status()).isEqualTo(ReservationStatus.CANCELLED_ON_TIME);
        var order = inOrder(lock, reservations);
        order.verify(lock).acquire();
        order.verify(reservations).findById(reservation.getId());
        verify(events).publishEvent(new AvailabilityChangedEvent(spaceId));
        verify(events).publishEvent(any(ReservationNotificationEvent.class));
        var notification = org.mockito.ArgumentCaptor.forClass(ReservationNotificationEvent.class);
        verify(events).publishEvent(notification.capture());
        assertThat(notification.getValue().cancellation().status()).isEqualTo(ReservationStatus.CANCELLED_ON_TIME);
        assertThat(notification.getValue().cancellation().reservedHours()).isEqualTo(1);
    }

    @Test
    void publishesNothingWhenReservationIsNotCancellable() {
        var reservation = reservation();
        reservation.cancelLate();
        when(reservations.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.cancel(userId, reservation.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("RESERVATION_NOT_CANCELLABLE");
        verify(events, never()).publishEvent(any(Object.class));
        verify(audit, never()).record(any(), any(), any(), any());
    }

    private Reservation reservation() {
        return new Reservation(UUID.randomUUID(), userId, spaceId, Instant.parse("2026-11-11T14:00:00Z"),
                Instant.parse("2026-11-11T15:00:00Z"), NOW, 60, 10);
    }
}
