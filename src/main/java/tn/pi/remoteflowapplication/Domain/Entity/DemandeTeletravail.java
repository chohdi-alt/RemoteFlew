package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@MappedSuperclass
public abstract class DemandeTeletravail extends AuditedEntity {

    @Column(name = "reference_code", unique = true, updatable = false)
    private String reference;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "type")
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private User createur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipe_id")
    private Team equipe;

    @OneToMany(mappedBy = "demandeTeletravail", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<Justificatif> justificatifs = new ArrayList<>();

    @OneToOne(mappedBy = "demandeTeletravail", cascade = CascadeType.ALL, orphanRemoval = true)
    private WorkflowInstance workflowInstance;

    @OneToMany(mappedBy = "demandeTeletravail", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<Notification> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "demandeTeletravail", cascade = CascadeType.ALL, orphanRemoval = true)
    protected final List<AuditLog> auditLogs = new ArrayList<>();

    protected void initDemandeMetadata(String employeeId, LocalDate dateDebut, LocalDate dateFin) {
        if (this.reference == null || this.reference.isBlank()) {
            this.reference = buildReference(employeeId, dateDebut, dateFin);
        }
        if (this.dateCreation == null) {
            this.dateCreation = LocalDateTime.now();
        }
        if (this.type == null) {
            this.type = inferType(dateDebut, dateFin);
        }
    }

    public void soumettreDemande() {
        registerAudit("SUBMIT", "DemandeTeletravail", "system");
    }

    public void annulerDemande(String utilisateur) {
        registerAudit("CANCEL", "DemandeTeletravail", utilisateur);
    }

    public List<AuditLog> consulterHistorique() {
        return Collections.unmodifiableList(auditLogs);
    }

    public String getReference() {
        return reference;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public String getType() {
        return type;
    }

    public User getCreateur() {
        return createur;
    }

    public Team getEquipe() {
        return equipe;
    }

    public void setCreateur(User createur) {
        this.createur = createur;
    }

    public void setEquipe(Team equipe) {
        this.equipe = equipe;
    }

    public List<Justificatif> getJustificatifs() {
        return Collections.unmodifiableList(justificatifs);
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public List<Notification> getNotifications() {
        return Collections.unmodifiableList(notifications);
    }

    public void addJustificatif(Justificatif justificatif) {
        justificatifs.add(justificatif);
    }

    public void bindWorkflowInstance(WorkflowInstance workflowInstance) {
        this.workflowInstance = workflowInstance;
    }

    public void addNotification(Notification notification) {
        notifications.add(notification);
    }

    /**
     * Delegates audit registration to subclasses to avoid base class knowing about
     * specific implementations.
     * Fixes Defect 5 (DDD Violation).
     */
    protected abstract void registerAudit(String action, String entite, String utilisateur);

    private String buildReference(String employeeId, LocalDate dateDebut, LocalDate dateFin) {
        String prefix = "DT";
        String employee = employeeId == null || employeeId.isBlank() ? "ANON" : employeeId;
        String dates = dateDebut == null || dateFin == null
                ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                : dateDebut + "_" + dateFin;
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "-" + employee + "-" + dates + "-" + unique;
    }

    private String inferType(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null) {
            return "STANDARD";
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        return days > 1 ? "SPECIAL" : "STANDARD";
    }
}
