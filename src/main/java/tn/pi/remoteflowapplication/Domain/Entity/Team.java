package tn.pi.remoteflowapplication.domain.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "teams")
public class Team extends Equipe {

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
}
