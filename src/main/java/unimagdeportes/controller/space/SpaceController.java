package unimagdeportes.controller.space;

import java.util.UUID;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import unimagdeportes.service.checkin.QrService;
import unimagdeportes.exception.BusinessException;

@RestController
public class SpaceController {
    private final SpaceService spaces;
    private final QrService qr;
    public SpaceController(SpaceService spaces, QrService qr) {
        this.spaces = spaces;
        this.qr = qr;
    }

    @GetMapping(value = "/admin/spaces/{id}/qr", produces = "image/svg+xml;charset=UTF-8")
    String qr(@PathVariable UUID id) { return qr.generate(id); }

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
    void image(@PathVariable UUID id, @PathVariable ImageKind kind, HttpServletResponse response) throws IOException {
        try (var image = spaces.image(id, kind)) {
            response.setContentType(image.contentType());
            response.setContentLengthLong(image.sizeBytes());
            response.setHeader("Cache-Control", "private, no-store");
            image.content().transferTo(response.getOutputStream());
        } catch (IOException exception) {
            if (response.isCommitted()) throw exception;
            resetImageResponse(response);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                    "No fue posible leer la imagen almacenada.", exception);
        }
    }

    private void resetImageResponse(HttpServletResponse response) {
        // Servlet has no portable removeHeader: reset the image while retaining CORS and security headers.
        var headers = new java.util.LinkedHashMap<String, java.util.List<String>>();
        for (String name : response.getHeaderNames()) {
            if (!name.equalsIgnoreCase("Content-Length") && !name.equalsIgnoreCase("Content-Type")
                    && !name.equalsIgnoreCase("Cache-Control")) {
                headers.put(name, java.util.List.copyOf(response.getHeaders(name)));
            }
        }
        response.reset();
        headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        response.setHeader("Cache-Control", "no-store");
    }

    @PutMapping("/admin/spaces/{id}")
    SpaceInfo update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestBody SpaceInput input) {
        return spaces.update(id, input, principal.id());
    }

}
