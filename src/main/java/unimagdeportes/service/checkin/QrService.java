package unimagdeportes.service.checkin;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.service.space.SpaceService;

@Service
public class QrService {
    private final SpaceService spaces;
    private final Environment environment;
    private final String frontendOrigin;

    public QrService(SpaceService spaces, Environment environment,
            @Value("${app.checkin.frontend-origin:}") String frontendOrigin) {
        this.spaces = spaces;
        this.environment = environment;
        this.frontendOrigin = frontendOrigin;
    }

    public String generate(UUID spaceId) {
        spaces.get(spaceId);
        URI origin = origin();
        String url = origin.toString().replaceAll("/$", "") + "/checkin/" + spaceId;
        try {
            var matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 4));
            var svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                    .append(matrix.getWidth()).append(' ').append(matrix.getHeight())
                    .append("\" role=\"img\" aria-label=\"Código QR para confirmar asistencia\">")
                    .append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/><path d=\"");
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) svg.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
            return svg.append("\" fill=\"black\"/></svg>").toString();
        } catch (WriterException ex) {
            throw new IllegalStateException("No se pudo generar el QR del espacio.", ex);
        }
    }

    private URI origin() {
        try {
            URI uri = URI.create(frontendOrigin);
            boolean httpAllowed = environment.acceptsProfiles(Profiles.of("dev", "test"));
            if (uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && (uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    && ("https".equalsIgnoreCase(uri.getScheme())
                    || httpAllowed && "http".equalsIgnoreCase(uri.getScheme()))) return uri;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "QR_NOT_CONFIGURED",
                    "El origen del cliente para el QR no está configurado correctamente.", ex);
        }
        throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "QR_NOT_CONFIGURED",
                "El origen del cliente para el QR no está configurado correctamente.");
    }
}
