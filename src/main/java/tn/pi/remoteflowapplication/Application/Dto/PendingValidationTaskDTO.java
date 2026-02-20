package tn.pi.remoteflowapplication.application.dto;

import java.time.LocalDate;

public record PendingValidationTaskDTO(
        Long requestId,
        String employeeName,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String taskKey
) {
}

