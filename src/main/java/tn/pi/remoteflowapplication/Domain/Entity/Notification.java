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
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "type")
    private String type;

    @Column(name = "date_envoi", nullable = false, updatable = false)
    private LocalDateTime dateEnvoi;

    @Column(name = "lue", nullable = false)
    private boolean lue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_id", nullable = false)
    private TeleworkRequest demandeTeletravail;

    protected Notification() {
    }

    public Notification(String message, String type, TeleworkRequest demandeTeletravail) {
        this.message = message;
        this.type = type;
        this.demandeTeletravail = demandeTeletravail;
        this.dateEnvoi = LocalDateTime.now();
        this.lue = false;
    }

    public void envoyer() {
        if (dateEnvoi == null) {
            dateEnvoi = LocalDateTime.now();
        }
    }

    public void marquerCommeLue() {
        this.lue = true;
    }
}
