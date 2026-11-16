package unimagdeportes.controller.reservation;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;
import unimagdeportes.exception.ApiError;

@RestControllerAdvice(assignableTypes = ReservationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReservationErrorHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformedRequest(HttpMessageNotReadableException exception) {
        String field = "$";
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
                var path = new StringBuilder();
                for (var reference : jackson.getPath()) {
                    if (reference.getPropertyName() != null) {
                        if (!path.isEmpty()) path.append('.');
                        path.append(reference.getPropertyName());
                    } else if (reference.getIndex() >= 0) {
                        path.append('[').append(reference.getIndex()).append(']');
                    }
                }
                if (!path.isEmpty()) field = path.toString();
                break;
            }
        }
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "La solicitud no es válida.",
                Map.of("fields", Map.of(field, "formato o valor inválido"))));
    }
}
