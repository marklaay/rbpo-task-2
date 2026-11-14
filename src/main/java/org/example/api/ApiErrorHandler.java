package org.example.api;

import java.time.Clock;
import java.util.Map;
import org.example.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorHandler {
    private final Clock clock;

    public ApiErrorHandler(Clock clock) {
        this.clock = clock;
    }

    private ResponseEntity<?> error(HttpStatus status, String message) {
        Map<String, Object> body = Map.of(
                "status", status.value(),
                "message", message,
                "timestamp", clock.instant()
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> business(ApiException exception) {
        return error(exception.status(), exception.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> malformed(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Некорректный JSON или параметр запроса");
    }
}
