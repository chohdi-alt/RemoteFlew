package tn.pi.remoteflowapplication.application.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ArchiveSummaryDTO(
        Long requestId,
        String employeeId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String managerExternalId,
        Instant managerDecisionAt,
        String managerComment,
        String hrExternalId,
        Instant hrDecisionAt,
        String hrComment,
        Instant submittedAt,
        boolean specialCase,
        String justificationReason,
        String justificatifFileId,
        String archiveNodeId
) {}
