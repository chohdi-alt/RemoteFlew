package tn.pi.remoteflowapplication.application.dto;

import java.time.LocalDate;

public class TeleworkStatusDTO {

    private Long requestId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String decisionComment;
    private boolean specialCase;
    
    private String justificationReason;
    private String justificatifFileId;
    private String managerComment;
    private String hrComment;

    public TeleworkStatusDTO() {
    }

    public TeleworkStatusDTO(
            Long requestId,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String decisionComment,
            boolean specialCase,
            String justificationReason,
            String justificatifFileId,
            String managerComment,
            String hrComment
    ) {
        this.requestId = requestId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.decisionComment = decisionComment;
        this.specialCase = specialCase;
        this.justificationReason = justificationReason;
        this.justificatifFileId = justificatifFileId;
        this.managerComment = managerComment;
        this.hrComment = hrComment;
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

    public String getJustificationReason() {
        return justificationReason;
    }

    public String getJustificatifFileId() {
        return justificatifFileId;
    }

    public String getManagerComment() {
        return managerComment;
    }

    public String getHrComment() {
        return hrComment;
    }
}
