package tn.pi.remoteflowapplication.application.dto;

import java.util.List;

public class UserDirectoryDTO {
    private Long id;
    private String externalId;
    private String email;
    private String nom;
    private String prenom;
    private List<String> roles;
    private String teamName;

    public UserDirectoryDTO() {
    }

    public UserDirectoryDTO(Long id, String externalId, String email, String nom, String prenom, List<String> roles,
            String teamName) {
        this.id = id;
        this.externalId = externalId;
        this.email = email;
        this.nom = nom;
        this.prenom = prenom;
        this.roles = roles;
        this.teamName = teamName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }
}
