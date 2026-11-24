package unimagdeportes.controller.block;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.dto.block.BlockDtos.CreateBlock;
import unimagdeportes.dto.block.BlockDtos.CreatedBlock;
import unimagdeportes.security.UserPrincipal;
import unimagdeportes.service.block.BlockService;

@RestController
public class BlockController {
    private final BlockService blocks;

    public BlockController(BlockService blocks) { this.blocks = blocks; }

    @PostMapping("/admin/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedBlock create(@AuthenticationPrincipal UserPrincipal principal, @RequestBody CreateBlock request) {
        return blocks.create(request, principal.id());
    }
}
