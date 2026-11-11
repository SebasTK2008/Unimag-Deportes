package unimagdeportes.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, ?> details;

    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public BusinessException(HttpStatus status, String code, String message, Map<String, ?> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public BusinessException(HttpStatus status, String code, String message, Throwable cause) {
        this(status, code, message);
        initCause(cause);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, ?> getDetails() { return details; }
}
