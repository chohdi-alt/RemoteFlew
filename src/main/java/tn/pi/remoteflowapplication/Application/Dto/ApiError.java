package tn.pi.remoteflowapplication.application.dto;

import java.time.LocalDateTime;

public record ApiError(
                LocalDateTime timestamp,
                int status,
                String error,
                String message,
                String path,
                Long retryAfterSeconds) {
    public ApiError(LocalDateTime timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null);
    }
}

