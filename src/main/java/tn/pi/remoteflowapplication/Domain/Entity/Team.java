package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "teams")
public class Team extends Equipe {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    protected Team() {
    }

    public Team(String name) {
        super(name, null, null);
    }

    public Team(String nom, String code, Integer effectif) {
        super(nom, code, effectif);
    }

    public String getName() {
        return getNom();
    }

    public User getManager() {
        return manager;
    }

    public void setManager(User manager) {
        this.manager = manager;
    }
}
