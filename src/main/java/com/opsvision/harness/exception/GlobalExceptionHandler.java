package com.opsvision.harness.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised HTTP error mapping for the chat lifecycle endpoints. Keeps
 * controllers free of try/catch boilerplate. Bare {@code Map} bodies match
 * the existing utility-endpoint style ({@code /samples}, {@code /tools},
 * {@code /session/health}); no validation library / wrapper DTO needed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ChatNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleChatNotFound(ChatNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "chat not found");
        body.put("reason", ex.getReason());
        if (ex.getChatId() != null) {
            body.put("chatId", ex.getChatId().toString());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "malformed " + ex.getName());
        body.put("value", String.valueOf(ex.getValue()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("400: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "bad request");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
