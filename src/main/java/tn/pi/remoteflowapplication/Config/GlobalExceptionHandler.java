package tn.pi.remoteflowapplication.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import tn.pi.remoteflowapplication.application.dto.ApiError;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied for this resource.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationExceptions(
            org.springframework.web.bind.MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", details, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {
        HttpStatusCode statusCode = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() == null ? "Request failed" : ex.getReason();
        return buildError(status, "REQUEST_REJECTED", message, request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {
        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected technical error occurred. Please contact support.",
                request);
    }

    private ResponseEntity<ApiError> buildError(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        ApiError error = new ApiError(
                LocalDateTime.now(),
                status.value(),
                code,
                message,
                request == null ? null : request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
