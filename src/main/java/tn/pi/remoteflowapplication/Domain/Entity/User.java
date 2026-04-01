package tn.pi.remoteflowapplication.domain.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends Utilisateur {

    @Column(name = "keycloak_id", unique = true, nullable = false, updatable = false)
    private String keycloakId;

    @Column(name = "external_id", unique = true, nullable = false)
    private String externalId;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

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

    public User(String keycloakId, String username, String fullName, String email) {
        super(email, extractNom(fullName), extractPrenom(fullName), username, true);
        this.keycloakId = requireIdentityValue(keycloakId, "keycloakId");
        this.externalId = this.keycloakId;
        this.username = requireIdentityValue(username, "username");
        this.fullName = normalizeFullName(fullName);
    }

    public User(String keycloakId, String email, String nom, String prenom, String username, boolean actif) {
        super(email, nom, prenom, username, actif);
        this.keycloakId = requireIdentityValue(keycloakId, "keycloakId");
        this.externalId = this.keycloakId;
        this.username = requireIdentityValue(username, "username");
        this.fullName = buildFullName(prenom, nom);
    }

    /**
     * Legacy constructor kept for backward compatibility with existing call sites.
     */
    public User(String externalId, String fullName, String email) {
        this(externalId, externalId, fullName, email);
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public String getUsername() {
        return username;
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

    public void synchronizeProfile(String username, String fullName, String email, boolean active) {
        this.username = requireIdentityValue(username, "username");
        setMatricule(this.username);
        setPrenom(extractPrenom(fullName));
        setNom(extractNom(fullName));
        setEmail(normalizeValue(email));
        setActif(active);
        this.fullName = normalizeFullName(fullName);
    }

    public void synchronizeIdentity(
            String keycloakId,
            String username,
            String email,
            String nom,
            String prenom,
            boolean active) {
        this.keycloakId = requireIdentityValue(keycloakId, "keycloakId");
        this.externalId = this.keycloakId;
        this.username = requireIdentityValue(username, "username");
        setNom(normalizeValue(nom));
        setPrenom(normalizeValue(prenom));
        setMatricule(this.username);
        setEmail(normalizeValue(email));
        setActif(active);
        this.fullName = buildFullName(prenom, nom);
    }

    public void synchronizeJwtProfile(
            String keycloakId,
            String username,
            String email,
            String fullName,
            boolean active) {
        this.keycloakId = requireIdentityValue(keycloakId, "keycloakId");
        this.externalId = this.keycloakId;
        this.username = requireIdentityValue(username, "username");
        setMatricule(this.username);
        setPrenom(extractPrenom(fullName));
        setNom(extractNom(fullName));
        setEmail(normalizeValue(email));
        setActif(active);
        this.fullName = normalizeFullName(fullName);
    }

    /**
     * Legacy sync API kept for existing call sites during transition.
     */
    public void synchronizeIdentity(String email, String nom, String prenom, String matricule, boolean active) {
        synchronizeIdentity(
                this.keycloakId,
                normalizeUsername(matricule, this.username),
                email,
                nom,
                prenom,
                active);
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
        if ((keycloakId == null || keycloakId.isBlank()) && externalId != null && !externalId.isBlank()) {
            this.keycloakId = externalId;
        }
        if (externalId == null || externalId.isBlank()) {
            this.externalId = requireIdentityValue(keycloakId, "externalId");
        }
        this.fullName = buildFullName(getPrenom(), getNom());
        if (username == null || username.isBlank()) {
            this.username = normalizeUsername(getMatricule(), keycloakId);
        }
        setMatricule(this.username);
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

    private static String requireIdentityValue(String value, String field) {
        String normalized = normalizeValue(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeUsername(String username, String fallback) {
        String normalized = normalizeValue(username);
        if (normalized != null) {
            return normalized;
        }
        return requireIdentityValue(fallback, "username");
    }
}
