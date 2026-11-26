package unimagdeportes.controller.block;

import org.springframework.http.HttpStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.dto.block.BlockDtos.CreateBlock;
import unimagdeportes.dto.block.BlockDtos.CreatedBlock;
import unimagdeportes.dto.block.BlockDtos.AdminBlock;
import unimagdeportes.dto.block.BlockDtos.BlockPage;
import unimagdeportes.dto.block.BlockDtos.CancelBlock;
import unimagdeportes.model.block.BlockStatus;
import unimagdeportes.security.UserPrincipal;
import unimagdeportes.service.block.BlockService;
import unimagdeportes.service.block.BlockCancellationService;
import unimagdeportes.service.block.BlockQueryService;

@RestController
public class BlockController {
    private final BlockService blocks;
    private final BlockQueryService query;
    private final BlockCancellationService cancellations;

    public BlockController(BlockService blocks, BlockQueryService query, BlockCancellationService cancellations) {
        this.blocks = blocks;
        this.query = query;
        this.cancellations = cancellations;
    }

    @PostMapping("/admin/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedBlock create(@AuthenticationPrincipal UserPrincipal principal, @RequestBody CreateBlock request) {
        return blocks.create(request, principal.id());
    }

    @GetMapping("/admin/blocks")
    BlockPage list(@RequestParam UUID spaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) BlockStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return query.list(new BlockQueryService.Filter(spaceId, from.toInstant(), to.toInstant(), status, page, size));
    }

    @PostMapping("/admin/blocks/{id}/cancellation")
    AdminBlock cancel(@PathVariable UUID id, @RequestBody CancelBlock request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return cancellations.cancel(id, request, principal.id());
    }
}
