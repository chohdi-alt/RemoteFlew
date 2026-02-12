package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_key", nullable = false)
    private String processKey;

    @Column(name = "statut", nullable = false)
    private String statut;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_id", nullable = false, unique = true)
    private TeleworkRequest demandeTeletravail;

    @OneToMany(mappedBy = "workflowInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<WorkflowTask> workflowTasks = new ArrayList<>();

    protected WorkflowInstance() {
    }

    public WorkflowInstance(String processKey, String statut, TeleworkRequest demandeTeletravail) {
        this.processKey = processKey;
        this.statut = statut;
        this.demandeTeletravail = demandeTeletravail;
    }

    public void demarrerWorkflow() {
        this.statut = "STARTED";
    }

    public void avancerWorkflow(String nouveauStatut) {
        this.statut = nouveauStatut;
    }

    public void addTask(WorkflowTask task) {
        workflowTasks.add(task);
    }

    public List<WorkflowTask> getWorkflowTasks() {
        return Collections.unmodifiableList(workflowTasks);
    }
}
