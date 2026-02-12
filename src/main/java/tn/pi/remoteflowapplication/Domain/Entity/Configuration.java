package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "business_configurations")
public class Configuration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cle", nullable = false, unique = true)
    private String cle;

    @Column(name = "valeur", nullable = false)
    private String valeur;

    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "configuration_telework_request",
            joinColumns = @JoinColumn(name = "configuration_id"),
            inverseJoinColumns = @JoinColumn(name = "telework_request_id"))
    private final Set<TeleworkRequest> demandesTeletravail = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "configuration_scoring_equipe",
            joinColumns = @JoinColumn(name = "configuration_id"),
            inverseJoinColumns = @JoinColumn(name = "scoring_equipe_id"))
    private final Set<ScoringEquipe> scoringEquipes = new HashSet<>();

    protected Configuration() {
    }

    public Configuration(String cle, String valeur) {
        this.cle = cle;
        this.valeur = valeur;
        this.dateModification = LocalDateTime.now();
    }

    public void definirSeuils(String valeur) {
        this.valeur = valeur;
        this.dateModification = LocalDateTime.now();
    }

    public void parametrerReglesMetier(String valeur) {
        definirSeuils(valeur);
    }

    public void appliquerSurDemande(TeleworkRequest demande) {
        demandesTeletravail.add(demande);
    }

    public void appliquerSurScoring(ScoringEquipe scoringEquipe) {
        scoringEquipes.add(scoringEquipe);
    }
}
