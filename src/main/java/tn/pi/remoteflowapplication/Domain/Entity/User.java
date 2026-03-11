package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends Utilisateur {

    @Column(name = "external_id", unique = true, nullable = false)
    private String externalId; // Keycloak userId

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public User(String externalId, String fullName, String email) {
        super(email, extractNom(fullName), extractPrenom(fullName), externalId, true);
        this.externalId = externalId;
        this.fullName = normalizeFullName(fullName);
    }

    public User(String externalId, String email, String nom, String prenom, String matricule, boolean actif) {
        super(email, nom, prenom, matricule, actif);
        this.externalId = externalId;
        this.fullName = buildFullName(prenom, nom);
    }

    public String getExternalId() {
        return externalId;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void addRole(Role role) {
        if (role != null) {
            this.roles.add(role);
        }
    }

    public void synchronizeProfile(String fullName, String email, boolean active) {
        setPrenom(extractPrenom(fullName));
        setNom(extractNom(fullName));
        setEmail(normalizeValue(email));
        setActif(active);
        this.fullName = normalizeFullName(fullName);
    }

    public void synchronizeIdentity(String email, String nom, String prenom, String matricule, boolean active) {
        setNom(normalizeValue(nom));
        setPrenom(normalizeValue(prenom));
        setMatricule(normalizeValue(matricule));
        setEmail(normalizeValue(email));
        setActif(active);
        this.fullName = buildFullName(prenom, nom);
    }

    public void updateActivation(boolean active) {
        setActif(active);
    }

    public void assignTeam(Team team) {
        setEquipe(team);
    }

    @PrePersist
    @PreUpdate
    private void generateFullName() {
        this.fullName = buildFullName(getPrenom(), getNom());
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

    private static String buildFullName(String prenom, String nom) {
        String p = prenom == null ? "" : prenom.trim();
        String n = nom == null ? "" : nom.trim();

        String value = (p + " " + n).trim();

        return value.isBlank() ? "N/A" : value;
    }

    private static String normalizeFullName(String fullName) {
        if (fullName == null) {
            return "N/A";
        }

        String trimmed = fullName.trim();

        return trimmed.isBlank() ? "N/A" : trimmed;
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isBlank() ? null : trimmed;
    }
}