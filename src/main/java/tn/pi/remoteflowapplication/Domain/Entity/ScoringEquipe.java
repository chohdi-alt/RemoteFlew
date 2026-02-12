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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "scoring_equipes")
public class ScoringEquipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "periode", nullable = false)
    private String periode;

    @Column(name = "taux_presence")
    private Double tauxPresence;

    @Column(name = "taux_teletravail")
    private Double tauxTeletravail;

    @Column(name = "score_global")
    private Double scoreGlobal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team equipe;

    @OneToMany(mappedBy = "scoringEquipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<Rapport> rapports = new ArrayList<>();

    protected ScoringEquipe() {
    }

    public ScoringEquipe(String periode, Team equipe) {
        this.periode = periode;
        this.equipe = equipe;
    }

    public void calculerScore() {
        double presence = tauxPresence == null ? 0.0 : tauxPresence;
        double teletravail = tauxTeletravail == null ? 0.0 : tauxTeletravail;
        this.scoreGlobal = (presence + teletravail) / 2.0;
    }

    public Double comparerPeriodePrecedente(ScoringEquipe precedent) {
        if (precedent == null || precedent.scoreGlobal == null || scoreGlobal == null) {
            return null;
        }
        return scoreGlobal - precedent.scoreGlobal;
    }

    public List<Rapport> getRapports() {
        return Collections.unmodifiableList(rapports);
    }
}
