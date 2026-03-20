package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "zeebe_tasks")
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "job_key", nullable = false, unique = true)
    private Long jobKey;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TaskEntity() {}

    public TaskEntity(Long requestId, Long jobKey, String type, String status, Instant createdAt) {
        this.requestId = requestId;
        this.jobKey = jobKey;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getJobKey() {
        return jobKey;
    }

    public void setJobKey(Long jobKey) {
        this.jobKey = jobKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
