package tn.pi.remoteflowapplication.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class ApprovalDecisionDTO {

    @JsonAlias({"hrComment", "managerComment"})
    private String comment;

    public ApprovalDecisionDTO() {
    }

    public ApprovalDecisionDTO(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
