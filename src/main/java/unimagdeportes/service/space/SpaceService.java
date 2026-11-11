package unimagdeportes.service.space;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.model.space.SpaceStatus;
import unimagdeportes.repository.space.SpaceRepository;

@Service
public class SpaceService {
    private final SpaceRepository spaces;

    public SpaceService(SpaceRepository spaces) { this.spaces = spaces; }

    @Transactional(readOnly = true)
    public SpaceInfo get(UUID id) {
        var space = spaces.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "El espacio no existe."));
        return new SpaceInfo(space.getId(), space.getName(), space.getCapacity(), space.getRequirements(),
                space.getOpeningTime(), space.getClosingTime(), space.getStatus(), space.getDisciplines());
    }

    public record SpaceInfo(UUID id, String name, int capacity, String requirements, LocalTime openingTime,
            LocalTime closingTime, SpaceStatus status, Set<Discipline> disciplines) {
        public SpaceInfo { disciplines = Set.copyOf(disciplines); }
    }
}
