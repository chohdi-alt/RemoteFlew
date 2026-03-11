package tn.pi.remoteflowapplication.infrastructure.security;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KeycloakAuthService {

    private final Keycloak keycloak;
    private final String realm;

    public KeycloakAuthService(Keycloak keycloak, @Value("${keycloak.realm}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    public UserRepresentation getUserById(String userId) {
        return keycloak.realm(realm)
                .users()
                .get(userId)
                .toRepresentation();
    }

    public List<String> getRealmRoles(String userId) {
        return keycloak.realm(realm)
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
        return keycloak.realm(realm)
                .users()
                .get(userId)
                .groups()
                .stream()
                .map(GroupRepresentation::getName)
                .toList();
    }

    public List<UserRepresentation> getAllUsers() {
        return keycloak.realm(realm)
                .users()
                .list();
    }

    public void setRealmRoles(String userId, Set<String> targetRoles) {
        var realm = keycloak.realm(this.realm);
        var realmLevel = realm.users().get(userId).roles().realmLevel();

        List<RoleRepresentation> currentRoles = realmLevel.listAll();
        if (!currentRoles.isEmpty()) {
            realmLevel.remove(currentRoles);
        }

        if (targetRoles == null || targetRoles.isEmpty()) {
            return;
        }

        Map<String, RoleRepresentation> availableRoles = realm.roles().list()
                .stream()
                .collect(Collectors.toMap(
                        role -> role.getName().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (a, b) -> a));

        List<RoleRepresentation> rolesToAssign = targetRoles.stream()
                .map(this::normalizeRole)
                .map(role -> availableRoles.get(role.toUpperCase(Locale.ROOT)))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        if (!rolesToAssign.isEmpty()) {
            realmLevel.add(rolesToAssign);
        }
    }

    public void setEnabled(String userId, boolean active) {
        var userResource = keycloak.realm(realm)
                .users()
                .get(userId);
        UserRepresentation representation = userResource.toRepresentation();
        representation.setEnabled(active);
        userResource.update(representation);
    }

    public String createUser(String username, String email, String firstName, String lastName) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);

        jakarta.ws.rs.core.Response response = keycloak.realm(realm).users().create(user);
        if (response.getStatus() == 201) {
            String path = response.getLocation().getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        }
        throw new RuntimeException(
                "Failed to create Keycloak user, status: " + response.getStatusInfo().getReasonPhrase());
    }

    public void resetPassword(String userId, String password) {
        org.keycloak.representations.idm.CredentialRepresentation cred = new org.keycloak.representations.idm.CredentialRepresentation();
        cred.setType(org.keycloak.representations.idm.CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(false);
        keycloak.realm(realm).users().get(userId).resetPassword(cred);
    }

    public List<RoleRepresentation> getAllRoles() {
        return keycloak.realm(realm).roles().list();
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        String value = role.trim();
        if (value.startsWith("ROLE_")) {
            return value.substring(5);
        }
        return value;
    }
}
