package unimagdeportes.service.space;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.space.SpaceImage;

@Component
public class SpaceImageValidator {
    static final long MAX_IMAGE_BYTES = 30L * 1024 * 1024;

    public SpaceImage read(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw invalid("La imagen es obligatoria y no debe superar 30 MiB.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                throw invalid("La imagen es obligatoria y no debe superar 30 MiB.");
            }
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid("El contenido no es una imagen reconocida.");
                var reader = readers.next();
                try {
                    reader.setInput(stream, true, true);
                    if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) {
                        throw invalid("El contenido no es una imagen válida.");
                    }
                    if (reader.read(0) == null) {
                        throw invalid("El contenido no es una imagen válida.");
                    }
                    String[] types = reader.getOriginatingProvider().getMIMETypes();
                    if (types.length == 0 || !types[0].startsWith("image/")) {
                        throw invalid("El formato de imagen no tiene un tipo de contenido válido.");
                    }
                    return new SpaceImage(types[0], bytes);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException exception) {
            throw invalid("No se pudo leer la imagen.");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, Map.of());
    }
}
