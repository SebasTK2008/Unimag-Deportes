package unimagdeportes.service.space;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import unimagdeportes.dto.space.SpaceDtos.ImageContent;
import unimagdeportes.dto.space.SpaceDtos.SpaceInfo;
import unimagdeportes.dto.space.SpaceDtos.SpaceInput;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.mapper.space.SpaceMapper;
import unimagdeportes.model.space.Space;
import unimagdeportes.model.space.SpaceStatus;
import unimagdeportes.model.space.SpaceImage;
import unimagdeportes.repository.space.SpaceRepository;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.reservation.ReservationLock;

@Service
public class SpaceService {
    private final SpaceRepository spaces;
    private final ReservationLock lock;
    private final AuditService audit;
    private final SpaceImageValidator images;
    private final SpaceMapper mapper;
    private final ApplicationEventPublisher events;

    public SpaceService(SpaceRepository spaces, ReservationLock lock, AuditService audit,
            SpaceImageValidator images, SpaceMapper mapper, ApplicationEventPublisher events) {
        this.spaces = spaces;
        this.lock = lock;
        this.audit = audit;
        this.images = images;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public SpaceInfo get(UUID id) {
        return mapper.toInfo(find(id));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public SpaceInfo getForUpdate(UUID id) {
        return mapper.toInfo(spaces.findForUpdate(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "El espacio no existe.")));
    }

    @Transactional(readOnly = true)
    public List<SpaceInfo> list() {
        return spaces.findAll().stream().map(mapper::toInfo).toList();
    }

    @Transactional
    public SpaceInfo create(SpaceInput input, MultipartFile preview, MultipartFile panorama, UUID administratorId) {
        lock.acquire();
        validate(input);
        var previewImage = images.read(preview);
        var panoramaImage = images.read(panorama);
        var space = new Space(UUID.randomUUID(), input.name(), input.capacity(), input.requirements(),
                input.openingTime(), input.closingTime(),
                input.status() == null ? SpaceStatus.ACTIVE : input.status(), input.disciplines(),
                previewImage, panoramaImage);
        spaces.save(space);
        audit.record("SPACE_CREATED", "SPACE", space.getId(), administratorId);
        return mapper.toInfo(space);
    }

    @Transactional(readOnly = true)
    public ImageContent image(UUID id, ImageKind kind) {
        SpaceImage image = image(find(id), kind);
        return mapper.toImageContent(image);
    }

    @Transactional
    public void replaceImage(UUID id, ImageKind kind, MultipartFile file, UUID administratorId) {
        lock.acquire();
        SpaceImage target = image(find(id), kind);
        SpaceImage replacement = images.read(file);
        target.replace(replacement.getContentType(), replacement.getContent());
        audit.record("SPACE_IMAGE_UPDATED", "SPACE", id, administratorId);
    }

    private SpaceImage image(Space space, ImageKind kind) {
        return kind == ImageKind.PREVIEW ? space.getPreviewImage() : space.getPanoramaImage();
    }

    public enum ImageKind { PREVIEW, PANORAMA }

    @Transactional
    public SpaceInfo update(UUID id, SpaceInput input, UUID administratorId) {
        lock.acquire();
        var space = find(id);
        var merged = new SpaceInput(input.name() == null ? space.getName() : input.name(),
                input.capacity() == null ? space.getCapacity() : input.capacity(),
                input.requirements() == null ? space.getRequirements() : input.requirements(),
                input.openingTime() == null ? space.getOpeningTime() : input.openingTime(),
                input.closingTime() == null ? space.getClosingTime() : input.closingTime(),
                input.status() == null ? space.getStatus() : input.status(),
                input.disciplines() == null ? space.getDisciplines() : input.disciplines());
        validate(merged);
        space.update(merged.name(), merged.capacity(), merged.requirements(), merged.openingTime(),
                merged.closingTime(), merged.status(), merged.disciplines());
        audit.record("SPACE_UPDATED", "SPACE", id, administratorId);
        events.publishEvent(new AvailabilityChangedEvent(id));
        return mapper.toInfo(space);
    }

    private Space find(UUID id) {
        return spaces.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "El espacio no existe."));
    }

    private void validate(SpaceInput input) {
        String field = null;
        String message = null;
        if (input.name() == null || input.name().isBlank() || input.name().length() > 200) {
            field = "name"; message = "debe contener entre 1 y 200 caracteres";
        } else if (input.capacity() == null || input.capacity() <= 0) {
            field = "capacity"; message = "debe ser mayor que cero";
        } else if (input.openingTime() == null) {
            field = "openingTime"; message = "es obligatorio";
        } else if (input.closingTime() == null || !input.closingTime().isAfter(input.openingTime())) {
            field = "closingTime"; message = "debe ser mayor que openingTime";
        } else if (input.disciplines() == null || input.disciplines().isEmpty()
                || input.disciplines().stream().anyMatch(java.util.Objects::isNull)) {
            field = "disciplines"; message = "debe contener al menos una disciplina válida";
        }
        if (field != null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "La solicitud contiene campos inválidos.", Map.of("fields", Map.of(field, message)));
        }
    }

}
