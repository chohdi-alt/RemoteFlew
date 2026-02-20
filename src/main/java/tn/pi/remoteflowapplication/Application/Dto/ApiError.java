package tn.pi.remoteflowapplication.application.dto;

import java.time.LocalDateTime;

public record ApiError(
        LocalDateTime timestamp,
        int status,
        String errorCode,
        String message,
        String path
) {
}

