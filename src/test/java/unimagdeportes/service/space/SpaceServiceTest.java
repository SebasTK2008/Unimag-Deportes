package unimagdeportes.service.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.HttpStatus;
import unimagdeportes.storage.ImageStorage;
import unimagdeportes.storage.StorageObjectCleanup;
import unimagdeportes.dto.space.SpaceDtos.SpaceInput;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.mapper.space.SpaceMapper;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.model.space.Space;
import unimagdeportes.model.space.SpaceStatus;
import unimagdeportes.model.space.SpaceImage;
import unimagdeportes.repository.space.SpaceRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.reservation.ReservationLock;

class SpaceServiceTest {
    private final SpaceRepository repository = mock(SpaceRepository.class);
    private final ReservationLock lock = mock(ReservationLock.class);
    private final AuditService audit = mock(AuditService.class);
    private final SpaceImageValidator images = mock(SpaceImageValidator.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ImageStorage storage = mock(ImageStorage.class);
    private final StorageObjectCleanup cleanup = mock(StorageObjectCleanup.class);
    private final SpaceService service = new SpaceService(repository, lock, audit, images,
            Mappers.getMapper(SpaceMapper.class), events, storage, cleanup, mock(PlatformTransactionManager.class));
    private final UUID administrator = UUID.randomUUID();
    private final MockMultipartFile file = new MockMultipartFile("image", new byte[]{1});

    @Test
    void createsActiveSpaceAndAuditsAfterAcquiringLock() {
        when(images.validate(file)).thenReturn("image/png");
        var result = service.create(input(), file, file, administrator);

        assertThat(result.status()).isEqualTo(SpaceStatus.ACTIVE);
        assertThat(result.requirements()).isNull();
        assertThat(result.disciplines()).containsExactly(Discipline.TENNIS);
        var order = inOrder(storage, lock, repository, audit);
        order.verify(storage, org.mockito.Mockito.times(2)).upload(any(), org.mockito.ArgumentMatchers.eq(file),
                org.mockito.ArgumentMatchers.eq("image/png"));
        order.verify(lock).acquire();
        order.verify(repository).save(any(Space.class));
        order.verify(audit).record("SPACE_CREATED", "SPACE", result.id(), administrator);
    }

    @Test
    void partialUpdatePreservesFieldsAndChangesToMaintenance() {
        UUID id = UUID.randomUUID();
        var existing = new Space(id, "Cancha", 4, null, LocalTime.of(8, 0),
                LocalTime.of(18, 0), SpaceStatus.ACTIVE, Set.of(Discipline.TENNIS), image(), image());
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        var result = service.update(id, new SpaceInput(null, null, null, null, null,
                SpaceStatus.MAINTENANCE, null), administrator);

        assertThat(result.status()).isEqualTo(SpaceStatus.MAINTENANCE);
        assertThat(result.name()).isEqualTo("Cancha");
        assertThat(result.disciplines()).containsExactly(Discipline.TENNIS);
        verify(audit).record("SPACE_UPDATED", "SPACE", id, administrator);
        verify(events).publishEvent(new AvailabilityChangedEvent(id));
    }

    @Test
    void rejectsInvalidHoursWithoutSaving() {
        var invalid = new SpaceInput("Cancha", 4, null, LocalTime.NOON, LocalTime.NOON,
                null, Set.of(Discipline.TENNIS));

        assertThatThrownBy(() -> service.create(invalid, file, file, administrator))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("VALIDATION_ERROR");
        verify(repository, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any());
        verifyNoInteractions(storage, cleanup);
    }

    @Test
    void validatesBothImagesBeforeUploadingAnything() {
        var panorama = new MockMultipartFile("panorama", new byte[]{2});
        when(images.validate(file)).thenReturn("image/png");
        when(images.validate(panorama)).thenThrow(new BusinessException(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid"));
        assertThatThrownBy(() -> service.create(input(), file, panorama, administrator))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(storage, cleanup, lock);
    }

    @Test
    void recordsCleanupBeforeAnAmbiguousUploadFailureAndDoesNotSaveReferences() {
        when(images.validate(file)).thenReturn("image/png");
        doThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "Unavailable"))
                .when(storage).upload(any(), any(), any());
        assertThatThrownBy(() -> service.create(input(), file, file, administrator))
                .isInstanceOf(BusinessException.class);
        var order = inOrder(cleanup, storage);
        order.verify(cleanup).trackUpload(any());
        order.verify(storage).upload(any(), any(), any());
        verifyNoInteractions(repository, lock, audit);
    }

    @Test
    void rejectsIncompatibleFootballDisciplinesBeforeValidatingImagesOrSaving() {
        var invalid = new SpaceInput("Cancha", 22, null, LocalTime.of(8, 0), LocalTime.of(18, 0),
                null, Set.of(Discipline.FOOTBALL_5, Discipline.FOOTBALL_11));

        assertThatThrownBy(() -> service.create(invalid, file, file, administrator))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("VALIDATION_ERROR");
        verify(images, never()).validate(any());
        verify(repository, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any());
        verifyNoInteractions(storage, cleanup);
    }

    @Test
    void rejectsIncompatibleUpdateWithoutChangingExistingSpace() {
        UUID id = UUID.randomUUID();
        var existing = new Space(id, "Cancha", 10, null, LocalTime.of(8, 0), LocalTime.of(18, 0),
                SpaceStatus.ACTIVE, Set.of(Discipline.FOOTBALL_5), image(), image());
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(id, new SpaceInput("Otro nombre", null, null, null, null,
                null, Set.of(Discipline.FOOTBALL_5, Discipline.FOOTBALL_11)), administrator))
                .isInstanceOf(BusinessException.class);
        assertThat(existing.getName()).isEqualTo("Cancha");
        assertThat(existing.getDisciplines()).containsExactly(Discipline.FOOTBALL_5);
        verify(audit, never()).record(any(), any(), any(), any());
        verify(events, never()).publishEvent(any(AvailabilityChangedEvent.class));
    }

    @Test
    void permitsEachFootballDisciplineAndOtherCombinations() {
        when(images.validate(file)).thenReturn("image/png");
        for (var disciplines : java.util.List.of(Set.of(Discipline.FOOTBALL_5),
                Set.of(Discipline.FOOTBALL_11), Set.of(Discipline.BASKETBALL, Discipline.VOLLEYBALL),
                Set.of(Discipline.FOOTBALL_5, Discipline.TENNIS))) {
            var input = new SpaceInput("Cancha", 22, null, LocalTime.of(8, 0), LocalTime.of(18, 0),
                    null, disciplines);
            assertThat(service.create(input, file, file, administrator).disciplines())
                    .containsExactlyInAnyOrderElementsOf(disciplines);
        }
    }

    @Test
    void repairsExistingIncompatibleSpaceByRemovingOneFootballDiscipline() {
        UUID id = UUID.randomUUID();
        var existing = new Space(id, "Cancha", 22, null, LocalTime.of(8, 0), LocalTime.of(18, 0),
                SpaceStatus.ACTIVE, Set.of(Discipline.FOOTBALL_5, Discipline.FOOTBALL_11), image(), image());
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(id,
                new SpaceInput("Otro nombre", null, null, null, null, null, null), administrator))
                .isInstanceOf(BusinessException.class);
        var repaired = service.update(id,
                new SpaceInput(null, null, null, null, null, null, Set.of(Discipline.FOOTBALL_11)),
                administrator);
        assertThat(repaired.disciplines()).containsExactly(Discipline.FOOTBALL_11);
    }

    @Test
    void replacesOnlyTheSelectedImage() {
        UUID id = UUID.randomUUID();
        var preview = image();
        var panorama = image();
        var existing = new Space(id, "Cancha", 4, null, LocalTime.of(8, 0), LocalTime.of(18, 0),
                SpaceStatus.ACTIVE, Set.of(Discipline.TENNIS), preview, panorama);
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(images.validate(file)).thenReturn("image/jpeg");
        String oldPreview = preview.getStorageKey();
        String oldPanorama = panorama.getStorageKey();

        service.replaceImage(id, SpaceService.ImageKind.PREVIEW, file, administrator);

        assertThat(preview.getStorageKey()).startsWith("spaces/" + id + "/preview/").isNotEqualTo(oldPreview);
        assertThat(preview.getContentType()).isEqualTo("image/jpeg");
        assertThat(panorama.getStorageKey()).isEqualTo(oldPanorama);
        verify(cleanup).scheduleDeletion(oldPreview);
        verify(audit).record("SPACE_IMAGE_UPDATED", "SPACE", id, administrator);
    }

    private SpaceInput input() {
        return new SpaceInput("Cancha", 4, null, LocalTime.of(8, 0), LocalTime.of(18, 0),
                null, Set.of(Discipline.TENNIS));
    }

    private SpaceImage image() {
        return new SpaceImage("image/png", "fixtures/" + UUID.randomUUID(), 1);
    }
}
