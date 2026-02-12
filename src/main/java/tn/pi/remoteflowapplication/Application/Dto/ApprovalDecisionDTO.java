package tn.pi.remoteflowapplication.application.dto;

public class ApprovalDecisionDTO {

    private Long requestId;
    private String managerId;
    private String comment;

    public ApprovalDecisionDTO() {
    }

    public ApprovalDecisionDTO(
            Long requestId,
            String managerId,
            String comment
    ) {
        this.requestId = requestId;
        this.managerId = managerId;
        this.comment = comment;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getManagerId() {
        return managerId;
    }

    public String getComment() {
        return comment;
    }
}
