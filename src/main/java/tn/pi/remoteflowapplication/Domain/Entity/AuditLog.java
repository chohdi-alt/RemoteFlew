package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entite", nullable = false)
    private String entite;

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "utilisateur")
    private String utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_id")
    private TeleworkRequest demandeTeletravail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_task_id")
    private WorkflowTask workflowTask;

    protected AuditLog() {
    }

    public AuditLog(String action, String entite, String utilisateur, TeleworkRequest demandeTeletravail) {
        this.action = action;
        this.entite = entite;
        this.utilisateur = utilisateur;
        this.demandeTeletravail = demandeTeletravail;
        this.timestamp = LocalDateTime.now();
    }

    public AuditLog(String action, String entite, String utilisateur, WorkflowTask workflowTask) {
        this.action = action;
        this.entite = entite;
        this.utilisateur = utilisateur;
        this.workflowTask = workflowTask;
        this.timestamp = LocalDateTime.now();
    }

    public void tracerActionMetier() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public String getAction() {
        return action;
    }

    public Long getId() {
        return id;
    }

    public String getEntite() {
        return entite;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUtilisateur() {
        return utilisateur;
    }
}
