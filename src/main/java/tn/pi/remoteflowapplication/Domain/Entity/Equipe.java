package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@MappedSuperclass
public abstract class Equipe extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String nom;

    @Column(name = "code", unique = true)
    private String code;

    @Column(name = "effectif")
    private Integer effectif;

    @OneToMany(mappedBy = "equipe", fetch = FetchType.LAZY)
    private final List<User> utilisateurs = new ArrayList<>();

    @OneToMany(mappedBy = "equipe", fetch = FetchType.LAZY)
    private final List<TeleworkRequest> demandesTeletravail = new ArrayList<>();

    @OneToMany(mappedBy = "equipe", fetch = FetchType.LAZY)
    private final List<ScoringEquipe> scoringEquipes = new ArrayList<>();

    protected Equipe() {
    }

    protected Equipe(String nom, String code, Integer effectif) {
        this.nom = nom;
        this.code = code;
        this.effectif = effectif;
    }

    public double calculerTauxPresence(int presents) {
        if (effectif == null || effectif <= 0) {
            return 0.0;
        }
        return (double) presents / effectif;
    }

    public double calculerTauxTeletravail(int teletravailleurs) {
        if (effectif == null || effectif <= 0) {
            return 0.0;
        }
        return (double) teletravailleurs / effectif;
    }

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public String getCode() {
        return code;
    }

    public Integer getEffectif() {
        return effectif;
    }

    public List<User> getUtilisateurs() {
        return Collections.unmodifiableList(utilisateurs);
    }

    public List<TeleworkRequest> getDemandesTeletravail() {
        return Collections.unmodifiableList(demandesTeletravail);
    }

    public List<ScoringEquipe> getScoringEquipes() {
        return Collections.unmodifiableList(scoringEquipes);
    }
}
