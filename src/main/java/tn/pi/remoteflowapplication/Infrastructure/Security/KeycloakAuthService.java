package tn.pi.remoteflowapplication.infrastructure.security;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KeycloakAuthService {

    private final Keycloak keycloak;

    public KeycloakAuthService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    public UserRepresentation getUserById(String userId) {
        return keycloak.realm("pfe-realm")
                .users()
                .get(userId)
                .toRepresentation();
    }

    public List<String> getRealmRoles(String userId) {
        return keycloak.realm("pfe-realm")
                .users()
                .get(userId)
                .roles()
                .realmLevel()
                .listEffective()
                .stream()
                .map(RoleRepresentation::getName)
                .toList();
    }

    public List<String> getGroups(String userId) {
        return keycloak.realm("pfe-realm")
                .users()
                .get(userId)
                .groups()
                .stream()
                .map(GroupRepresentation::getName)
                .toList();
    }

    public List<UserRepresentation> getAllUsers() {
        return keycloak.realm("pfe-realm")
                .users()
                .list();
    }
}
