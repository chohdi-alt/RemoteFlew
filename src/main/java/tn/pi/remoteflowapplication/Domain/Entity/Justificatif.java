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
@Table(name = "justificatifs")
public class Justificatif {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "type")
    private String type;

    @Column(name = "date_ajout", nullable = false, updatable = false)
    private LocalDateTime dateAjout;

    @Column(name = "alfresco_node_id")
    private String alfrescoNodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_id", nullable = false)
    private TeleworkRequest demandeTeletravail;

    protected Justificatif() {
    }

    public Justificatif(String nomFichier, String type, String alfrescoNodeId, TeleworkRequest demandeTeletravail) {
        this.nomFichier = nomFichier;
        this.type = type;
        this.alfrescoNodeId = alfrescoNodeId;
        this.demandeTeletravail = demandeTeletravail;
        this.dateAjout = LocalDateTime.now();
    }

    public void televerser() {
        // Upload is handled by AlfrescoDocumentService; this method keeps domain intent explicit.
    }

    public void consulter() {
        // Download/consultation is handled by infrastructure adapters.
    }

    public Long getId() {
        return id;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public String getType() {
        return type;
    }

    public LocalDateTime getDateAjout() {
        return dateAjout;
    }

    public String getAlfrescoNodeId() {
        return alfrescoNodeId;
    }
}
