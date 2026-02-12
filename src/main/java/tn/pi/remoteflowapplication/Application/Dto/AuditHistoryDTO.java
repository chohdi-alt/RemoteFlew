package tn.pi.remoteflowapplication.application.dto;

import java.time.LocalDateTime;

public class AuditHistoryDTO {
    private String action;
    private String entity;
    private LocalDateTime timestamp;
    private String user;

    public AuditHistoryDTO(String action, String entity, LocalDateTime timestamp, String user) {
        this.action = action;
        this.entity = entity;
        this.timestamp = timestamp;
        this.user = user;
    }

    public String getAction() {
        return action;
    }

    public String getEntity() {
        return entity;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUser() {
        return user;
    }
}
