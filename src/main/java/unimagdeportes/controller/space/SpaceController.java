package unimagdeportes.controller.space;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import unimagdeportes.dto.space.SpaceDtos.SpaceInfo;
import unimagdeportes.dto.space.SpaceDtos.SpaceInput;
import unimagdeportes.dto.space.SpaceDtos.SpaceList;
import unimagdeportes.security.UserPrincipal;
import unimagdeportes.service.space.SpaceService;
import unimagdeportes.service.space.SpaceService.ImageKind;

@RestController
public class SpaceController {
    private final SpaceService spaces;

    public SpaceController(SpaceService spaces) { this.spaces = spaces; }

    @GetMapping("/admin/spaces")
    SpaceList list() { return new SpaceList(spaces.list()); }

    @PostMapping(value = "/admin/spaces", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    SpaceInfo create(@AuthenticationPrincipal UserPrincipal principal, @RequestPart("space") SpaceInput input,
            @RequestPart("preview") MultipartFile preview, @RequestPart("panorama") MultipartFile panorama) {
        return spaces.create(input, preview, panorama, principal.id());
    }

    @PutMapping(value = "/admin/spaces/{id}/images/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void replaceImage(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @PathVariable ImageKind kind, @RequestPart("image") MultipartFile image) {
        spaces.replaceImage(id, kind, image, principal.id());
    }

    @GetMapping("/spaces/{id}/images/{kind}")
    ResponseEntity<byte[]> image(@PathVariable UUID id, @PathVariable ImageKind kind) {
        var image = spaces.image(id, kind);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).body(image.content());
    }

    @PutMapping("/admin/spaces/{id}")
    SpaceInfo update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestBody SpaceInput input) {
        return spaces.update(id, input, principal.id());
    }

}
