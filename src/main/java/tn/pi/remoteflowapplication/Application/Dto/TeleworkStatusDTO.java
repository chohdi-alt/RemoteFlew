package tn.pi.remoteflowapplication.application.dto;

import java.time.LocalDate;

public class TeleworkStatusDTO {

    private Long requestId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String decisionComment;
    private boolean specialCase;

    public TeleworkStatusDTO() {
    }

    public TeleworkStatusDTO(
            Long requestId,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String decisionComment,
            boolean specialCase
    ) {
        this.requestId = requestId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.decisionComment = decisionComment;
        this.specialCase = specialCase;
    }

    public Long getRequestId() {
        return requestId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getStatus() {
        return status;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public boolean isSpecialCase() {
        return specialCase;
    }
}
