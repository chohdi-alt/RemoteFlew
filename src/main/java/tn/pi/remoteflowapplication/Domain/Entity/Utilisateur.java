package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@MappedSuperclass
public abstract class Utilisateur extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "nom")
    private String nom;

    @Column(name = "prenom")
    private String prenom;

    @Column(name = "matricule")
    private String matricule;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team equipe;

    @OneToMany(mappedBy = "createur")
    private final List<TeleworkRequest> demandes = new ArrayList<>();

    protected Utilisateur() {
    }

    protected Utilisateur(String email, String nom, String prenom, String matricule, boolean actif) {
        this.email = email;
        this.nom = nom;
        this.prenom = prenom;
        this.matricule = matricule;
        this.actif = actif;
    }

    public TeleworkRequest creerDemandeTeletravail(java.time.LocalDate dateDebut, java.time.LocalDate dateFin) {
        return TeleworkRequest.create(resolveEmployeeId(), dateDebut, dateFin);
    }

    public boolean peutValiderDemande(String role) {
        return role != null && (role.equals("ROLE_MANAGER") || role.equals("ROLE_HR"));
    }

    public List<TeleworkRequest> consulterDashboard() {
        return Collections.unmodifiableList(demandes);
    }

    protected String resolveEmployeeId() {
        if (matricule != null && !matricule.isBlank()) {
            return matricule;
        }
        return id == null ? "unknown-user" : String.valueOf(id);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public String getMatricule() {
        return matricule;
    }

    public boolean isActif() {
        return actif;
    }

    public Team getEquipe() {
        return equipe;
    }

    protected void setEmail(String email) {
        this.email = email;
    }

    protected void setNom(String nom) {
        this.nom = nom;
    }

    protected void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    protected void setMatricule(String matricule) {
        this.matricule = matricule;
    }

    protected void setActif(boolean actif) {
        this.actif = actif;
    }

    protected void setEquipe(Team equipe) {
        this.equipe = equipe;
    }
}
