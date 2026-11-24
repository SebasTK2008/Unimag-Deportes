package unimagdeportes.service.space;

import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import unimagdeportes.exception.BusinessException;

@Component
public class SpaceImageValidator {
    static final long MAX_IMAGE_BYTES = 30L * 1024 * 1024;
    private final long maxPixels;
    private final int maxDimension;
    private static final int VALIDATION_DIMENSION = 1024;

    public SpaceImageValidator(@Value("${app.storage.max-image-pixels:64000000}") long maxPixels,
            @Value("${app.storage.max-image-dimension:16384}") int maxDimension) {
        if (maxPixels < 1 || maxDimension < 1) {
            throw new IllegalArgumentException("Los límites de dimensiones deben ser positivos.");
        }
        this.maxPixels = maxPixels;
        this.maxDimension = maxDimension;
    }

    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw invalid("La imagen es obligatoria y no debe superar 30 MiB.");
        }
        try {
            try (var input = file.getInputStream(); var stream = ImageIO.createImageInputStream(input)) {
                if (stream == null) throw invalid("El contenido no es una imagen reconocida.");
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid("El contenido no es una imagen reconocida.");
                var reader = readers.next();
                try {
                    reader.setInput(stream, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0) {
                        throw invalid("El contenido no es una imagen válida.");
                    }
                    if ((long) width * height > maxPixels || width > maxDimension || height > maxDimension) {
                        throw invalid("La imagen supera el límite de píxeles permitido.");
                    }
                    // Validate decoding without allocating a full-resolution raster. Stored bytes are unchanged.
                    var parameters = reader.getDefaultReadParam();
                    parameters.setSourceSubsampling(1 + (width - 1) / VALIDATION_DIMENSION,
                            1 + (height - 1) / VALIDATION_DIMENSION, 0, 0);
                    var decoded = reader.read(0, parameters);
                    if (decoded == null) {
                        throw invalid("El contenido no es una imagen válida.");
                    }
                    decoded.flush();
                    String[] types = reader.getOriginatingProvider().getMIMETypes();
                    if (types.length == 0 || !types[0].startsWith("image/")) {
                        throw invalid("El formato de imagen no tiene un tipo de contenido válido.");
                    }
                    return types[0];
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "No se pudo leer la imagen.", exception);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, Map.of());
    }
}
