package unimagdeportes.service.block;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.dto.block.BlockDtos.BlockPage;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.block.BlockStatus;
import unimagdeportes.repository.block.InstitutionalBlockRepository;
import unimagdeportes.service.space.SpaceService;

@Service
public class BlockQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private final InstitutionalBlockRepository blocks;
    private final SpaceService spaces;
    private final BlockViewService views;

    public BlockQueryService(InstitutionalBlockRepository blocks, SpaceService spaces, BlockViewService views) {
        this.blocks = blocks;
        this.spaces = spaces;
        this.views = views;
    }

    public record Filter(UUID spaceId, Instant from, Instant to, BlockStatus status, int page, int size) { }

    @Transactional(readOnly = true)
    public BlockPage list(Filter filter) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (filter.spaceId() == null) fields.put("spaceId", "es obligatorio");
        if (filter.from() == null) fields.put("from", "es obligatorio");
        if (filter.to() == null) fields.put("to", "es obligatorio");
        if (filter.from() != null && filter.to() != null && !filter.to().isAfter(filter.from())) {
            fields.put("to", "debe ser posterior a from");
        }
        if (filter.page() < 0) fields.put("page", "debe ser no negativo");
        if (filter.size() < 1 || filter.size() > MAX_PAGE_SIZE) fields.put("size", "debe estar entre 1 y 100");
        if (!fields.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Los filtros de bloqueos son inválidos.", Map.of("fields", fields));
        }
        spaces.get(filter.spaceId());
        var page = blocks.findHistory(filter.spaceId(), filter.from(), filter.to(), filter.status(),
                PageRequest.of(filter.page(), filter.size(), Sort.by("startAt", "id")));
        return new BlockPage(views.describe(page.getContent()),
                filter.page(), filter.size(), page.getTotalElements());
    }
}
