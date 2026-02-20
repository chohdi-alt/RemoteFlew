package tn.pi.remoteflowapplication.application.dto;

import java.time.LocalDateTime;

public record AuditLogDTO(
        Long id,
        String action,
        String entity,
        LocalDateTime timestamp,
        String actor
) {
}

