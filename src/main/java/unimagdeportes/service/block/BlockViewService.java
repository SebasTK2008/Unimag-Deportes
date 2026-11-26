package unimagdeportes.service.block;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.dto.block.BlockDtos.AdminBlock;
import unimagdeportes.mapper.block.BlockMapper;
import unimagdeportes.model.block.InstitutionalBlock;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.user.UserService;

/** Enriches administrative views in batches, without exposing profiles or changing historical actors. */
@Service
public class BlockViewService {
    private final AuditService audit;
    private final UserService users;
    private final BlockMapper mapper;

    public BlockViewService(AuditService audit, UserService users, BlockMapper mapper) {
        this.audit = audit;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AdminBlock> describe(List<InstitutionalBlock> blocks) {
        if (blocks.isEmpty()) return List.of();
        var creators = audit.blockCreators(blocks.stream().map(InstitutionalBlock::getId).toList());
        var actorIds = new HashSet<UUID>(creators.values());
        for (var block : blocks) {
            if (block.getCancelledBy() != null) actorIds.add(block.getCancelledBy());
        }
        var names = users.displayNames(actorIds);
        return blocks.stream().map(block -> {
            UUID creator = creators.get(block.getId());
            return mapper.toAdminBlock(block, creator, creator == null ? null : names.get(creator),
                    block.getCancelledBy() == null ? null : names.get(block.getCancelledBy()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public AdminBlock describe(InstitutionalBlock block) {
        return describe(List.of(block)).getFirst();
    }
}
