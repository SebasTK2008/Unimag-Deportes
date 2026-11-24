package unimagdeportes.dto.space;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.model.space.SpaceStatus;

public final class SpaceDtos {
    private SpaceDtos() { }

    public record SpaceInput(String name, Integer capacity, String requirements, LocalTime openingTime,
            LocalTime closingTime, SpaceStatus status, Set<Discipline> disciplines) { }

    public record SpaceInfo(UUID id, String name, int capacity, String requirements, LocalTime openingTime,
            LocalTime closingTime, SpaceStatus status, Set<Discipline> disciplines) {
        public SpaceInfo { disciplines = Set.copyOf(disciplines); }
    }

    public record SpaceList(List<SpaceInfo> spaces) { }

    public record ImageReference(String contentType, String storageKey, long sizeBytes) { }
}
