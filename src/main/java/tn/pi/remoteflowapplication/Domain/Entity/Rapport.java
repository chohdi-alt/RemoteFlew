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
@Table(name = "rapports")
public class Rapport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "periode", nullable = false)
    private String periode;

    @Column(name = "date_generation", nullable = false)
    private LocalDateTime dateGeneration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scoring_equipe_id", nullable = false)
    private ScoringEquipe scoringEquipe;

    protected Rapport() {
    }

    public Rapport(String type, String periode, ScoringEquipe scoringEquipe) {
        this.type = type;
        this.periode = periode;
        this.scoringEquipe = scoringEquipe;
        this.dateGeneration = LocalDateTime.now();
    }

    public byte[] genererPdf() {
        return ("PDF report " + periode + " " + type).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] genererExcel() {
        return ("Excel report " + periode + " " + type).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] exporter(String format) {
        if ("EXCEL".equalsIgnoreCase(format)) {
            return genererExcel();
        }
        return genererPdf();
    }
}
