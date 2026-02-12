package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workflow_tasks")
public class WorkflowTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "assignee")
    private String assignee;

    @Column(name = "date_echeance")
    private LocalDateTime dateEcheance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @OneToMany(mappedBy = "workflowTask", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<AuditLog> auditLogs = new ArrayList<>();

    protected WorkflowTask() {
    }

    public WorkflowTask(String nom, String assignee, LocalDateTime dateEcheance, WorkflowInstance workflowInstance) {
        this.nom = nom;
        this.assignee = assignee;
        this.dateEcheance = dateEcheance;
        this.workflowInstance = workflowInstance;
    }

    public void assigner(String assignee) {
        this.assignee = assignee;
    }

    public void completer(String utilisateur) {
        this.auditLogs.add(new AuditLog("COMPLETE_TASK", "WorkflowTask", utilisateur, this));
    }
}
