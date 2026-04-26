package com.livana.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 401 ───────────────────────────────────────────────────────────────────
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex,
                                                             HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req.getRequestURI());
    }

    // ── 403 ───────────────────────────────────────────────────────────────────
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex,
                                                          HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req.getRequestURI());
    }

    // ── 400 Bean Validation ──────────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        ErrorResponse body = new ErrorResponse(
                Instant.now().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                req.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── 409 DB constraint violations (FK, unique) ────────────────────────────
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            org.springframework.dao.DataIntegrityViolationException ex,
            HttpServletRequest req) {
        // Surface a friendly message without leaking DB internals
        String message = "Request violates a data constraint — check that referenced IDs exist";
        return build(HttpStatus.CONFLICT, message, req.getRequestURI());
    }

    // ── 500 Catch-all ─────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex,
                                                        HttpServletRequest req) {
        // Log it so we can see the real cause in the terminal
        ex.printStackTrace();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", req.getRequestURI());
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String path) {
        ErrorResponse body = new ErrorResponse(
                Instant.now().toString(),
                status.value(),
                message,
                path,
                null
        );
        return ResponseEntity.status(status).body(body);
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────
    public record ErrorResponse(
            String timestamp,
            int status,
            String message,
            String path,
            Map<String, String> fieldErrors
    ) {}
}
