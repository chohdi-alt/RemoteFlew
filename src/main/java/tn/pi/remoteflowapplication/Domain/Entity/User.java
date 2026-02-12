package tn.pi.remoteflowapplication.domain.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User extends Utilisateur {

    @Column(name = "external_id", unique = true, nullable = false)
    private String externalId; // Keycloak userId

    protected User() {
    }

    public User(String externalId, String fullName, String email) {
        super(email, extractNom(fullName), extractPrenom(fullName), externalId, true);
        this.externalId = externalId;
    }

    public User(String externalId, String email, String nom, String prenom, String matricule, boolean actif) {
        super(email, nom, prenom, matricule, actif);
        this.externalId = externalId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getFullName() {
        String nom = getNom() == null ? "" : getNom().trim();
        String prenom = getPrenom() == null ? "" : getPrenom().trim();
        String value = (prenom + " " + nom).trim();
        return value.isBlank() ? "N/A" : value;
    }

    private static String extractPrenom(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Unknown";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    private static String extractNom(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "User";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (i > 1) {
                sb.append(' ');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
