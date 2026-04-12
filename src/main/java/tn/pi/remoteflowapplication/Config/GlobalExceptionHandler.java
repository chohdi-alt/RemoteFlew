package tn.pi.remoteflowapplication.config;

import jakarta.validation.ConstraintViolationException;
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
import tn.pi.remoteflowapplication.domain.exception.ResourceNotFoundException;
import tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException;
import tn.pi.remoteflowapplication.domain.exception.WorkflowExecutionException;
import tn.pi.remoteflowapplication.domain.exception.BadRequestException;
import tn.pi.remoteflowapplication.domain.exception.AuthenticationFailedException;
import tn.pi.remoteflowapplication.domain.exception.ExternalServiceException;
import tn.pi.remoteflowapplication.domain.exception.KeycloakConflictException;
import tn.pi.remoteflowapplication.domain.exception.UserAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION", ex.getMessage(), request);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleUserAlreadyExistsException(
            UserAlreadyExistsException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(KeycloakConflictException.class)
    public ResponseEntity<ApiError> handleKeycloakConflictException(
            KeycloakConflictException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, "KEYCLOAK_CONFLICT", ex.getMessage(), request);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiError> handleExternalServiceException(
            ExternalServiceException ex,
            HttpServletRequest request) {
        logger.error(
                "event=EXTERNAL_SERVICE_FAILURE path={} operation={} upstreamStatus={} upstreamUrl={} upstreamBody={} message={}",
                request == null ? null : request.getRequestURI(),
                ex.getOperation(),
                ex.getUpstreamStatus(),
                ex.getUpstreamUrl(),
                ex.getUpstreamBody(),
                ex.getMessage());
        return buildError(HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR", ex.getMessage(), request);
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
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(
            org.springframework.web.bind.MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<ValidationFieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationFieldError(
                        error.getField(),
                        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(buildValidationError("Request validation failed", fields, request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        List<ValidationFieldError> fields = ex.getConstraintViolations().stream()
                .map(violation -> new ValidationFieldError(
                        violation.getPropertyPath() == null ? "unknown" : violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();

        return ResponseEntity.badRequest().body(buildValidationError("Request validation failed", fields, request));
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

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFoundException(ResourceNotFoundException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiError> handleForbiddenOperationException(ForbiddenOperationException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequestException(BadRequestException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(WorkflowExecutionException.class)
    public ResponseEntity<ApiError> handleWorkflowExecutionException(WorkflowExecutionException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailedException(AuthenticationFailedException ex,
            HttpServletRequest request) {
        String ip = request == null ? "unknown" : request.getRemoteAddr();
        String path = request == null ? "unknown" : request.getRequestURI();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String user = (auth != null) ? auth.getName() : "anonymous";

        logger.warn("AUTH_ERROR",
                kv("event", ex.getAuthErrorCode().name()),
                kv("event_normalized", ex.getAuthErrorCode().getNormalizedName()),
                kv("category", "AUTH"),
                kv("outcome", "FAILURE"),
                kv("ip", ip),
                kv("path", path),
                kv("user", user),
                kv("errorCode", ex.getErrorCode()),
                kv("traceId", MDC.get("traceId")));

        String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Invalid credentials or Keycloak rejected password grant"
                : ex.getMessage();

        String apiErrorCode = resolveApiErrorCode(ex);
        ApiError error = new ApiError(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                apiErrorCode,
                message,
                request == null ? null : request.getRequestURI(),
                ex.getRetryAfterSeconds());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDatabaseError(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        return buildError(
                HttpStatus.BAD_REQUEST,
                "DATABASE_CONSTRAINT",
                "Database constraint violation",
                request);
    }

    @ExceptionHandler(jakarta.persistence.OptimisticLockException.class)
    public ResponseEntity<ApiError> handleOptimisticLockException(
            jakarta.persistence.OptimisticLockException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, "CONFLICT", "The resource was modified by another transaction.",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception ex, HttpServletRequest request) {
        logger.error("event=INTERNAL_SERVER_ERROR path={} message={}",
                request == null ? null : request.getRequestURI(),
                ex.getMessage(),
                ex);
        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please contact support.",
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

    private ValidationErrorResponse buildValidationError(
            String message,
            List<ValidationFieldError> fields,
            HttpServletRequest request) {
        return new ValidationErrorResponse(
                LocalDateTime.now(),
                "VALIDATION_ERROR",
                message,
                request == null ? null : request.getRequestURI(),
                fields);
    }

    public record ValidationFieldError(String field, String message) {
    }

    public record ValidationErrorResponse(
            LocalDateTime timestamp,
            String error,
            String message,
            String path,
            List<ValidationFieldError> fields) {
    }

    private String resolveApiErrorCode(AuthenticationFailedException exception) {
        String code = exception.getErrorCode();
        if (code == null || code.isBlank()) {
            return exception.getAuthErrorCode().name();
        }

        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if ("AUTHENTICATION_FAILED".equals(normalized)
                || "AUTH_INVALID".equals(normalized)
                || "AUTH_TEMP_LOCK".equals(normalized)
                || "AUTH_ACCOUNT_DISABLED".equals(normalized)
                || "PASSWORD_UPDATE_REQUIRED".equals(normalized)) {
            return exception.getAuthErrorCode().name();
        }

        // Preserve legacy/non-auth codes (e.g. ACTIVATION_TOKEN_INVALID).
        return code;
    }
}
