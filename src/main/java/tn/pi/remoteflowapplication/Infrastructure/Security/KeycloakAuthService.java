package tn.pi.remoteflowapplication.infrastructure.security;

import jakarta.ws.rs.NotFoundException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KeycloakAuthService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakAuthService.class);
    private static final String DEFAULT_TEMPORARY_PASSWORD = "remoteflow";
    private static final String REQUIRED_ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";

    private final Keycloak keycloak;
    private final String realm;

    public KeycloakAuthService(Keycloak keycloak, @Value("${keycloak.realm}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    public UserRepresentation getUserById(String userId) {
        return realmResource()
                .users()
                .get(userId)
                .toRepresentation();
    }

    public List<String> getRealmRoles(String userId) {
        return realmResource()
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
        return realmResource()
                .users()
                .get(userId)
                .groups()
                .stream()
                .map(GroupRepresentation::getName)
                .toList();
    }

    public List<UserRepresentation> getAllUsers() {
        return realmResource()
                .users()
                .list();
    }

    public void setRealmRoles(String userId, Set<String> targetRoles) {
        RealmResource realmResource = realmResource();
        var realmLevel = realmResource.users().get(userId).roles().realmLevel();

        List<RoleRepresentation> currentRoles = realmLevel.listAll();
        if (!currentRoles.isEmpty()) {
            realmLevel.remove(currentRoles);
        }

        Set<String> normalizedTargets = normalizeRoles(targetRoles);
        if (normalizedTargets.isEmpty()) {
            return;
        }

        Map<String, RoleRepresentation> availableRoles = realmResource.roles().list()
                .stream()
                .collect(Collectors.toMap(
                        role -> role.getName().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (a, b) -> a));

        List<RoleRepresentation> rolesToAssign = normalizedTargets.stream()
                .map(roleName -> resolveOrCreateRealmRole(realmResource, availableRoles, roleName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (rolesToAssign.size() != normalizedTargets.size()) {
            throw new IllegalStateException("Failed to resolve all requested Keycloak realm roles for user " + userId);
        }

        realmLevel.add(rolesToAssign);
    }

    public void assignUserToManagedGroup(String userId, String targetGroupName, Set<String> managedGroupNames) {
        String normalizedTargetGroup = normalizeGroupName(targetGroupName);
        if (normalizedTargetGroup.isBlank()) {
            throw new IllegalArgumentException("Target Keycloak group name is required.");
        }

        Set<String> normalizedManagedGroups = normalizeGroupNames(managedGroupNames);
        if (normalizedManagedGroups.isEmpty()) {
            normalizedManagedGroups = Set.of(normalizedTargetGroup);
        } else if (!normalizedManagedGroups.contains(normalizedTargetGroup)) {
            normalizedManagedGroups = new LinkedHashSet<>(normalizedManagedGroups);
            normalizedManagedGroups.add(normalizedTargetGroup);
        }

        RealmResource realmResource = realmResource();
        var userResource = realmResource.users().get(userId);

        GroupRepresentation targetGroup = findGroupByName(realmResource, normalizedTargetGroup)
                .orElseGet(() -> createGroup(realmResource, normalizedTargetGroup));

        Set<String> currentGroupIds = userResource.groups().stream()
                .map(GroupRepresentation::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        for (String managedGroupName : normalizedManagedGroups) {
            if (managedGroupName.equals(normalizedTargetGroup)) {
                continue;
            }

            findGroupByName(realmResource, managedGroupName)
                    .map(GroupRepresentation::getId)
                    .filter(groupId -> groupId != null && !groupId.isBlank())
                    .filter(currentGroupIds::contains)
                    .ifPresent(userResource::leaveGroup);
        }

        if (targetGroup.getId() == null || targetGroup.getId().isBlank()) {
            throw new IllegalStateException("Unable to resolve Keycloak group id for group " + normalizedTargetGroup);
        }

        if (!currentGroupIds.contains(targetGroup.getId())) {
            userResource.joinGroup(targetGroup.getId());
        }
    }

    public void deleteUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        try {
            realmResource().users().delete(userId);
        } catch (Exception ex) {
            logger.warn("event=KEYCLOAK_USER_DELETE_FAILED userId={} reason={}", userId, ex.getMessage(), ex);
        }
    }

    public void setEnabled(String userId, boolean active) {
        var userResource = realmResource()
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
        user.setRequiredActions(List.of(REQUIRED_ACTION_UPDATE_PASSWORD));
        user.setCredentials(List.of(buildTemporaryPasswordCredential()));

        try (jakarta.ws.rs.core.Response response = realmResource().users().create(user)) {
            if (response.getStatus() == 201) {
                String path = response.getLocation().getPath();
                return path.substring(path.lastIndexOf('/') + 1);
            }
            throw new RuntimeException(
                    "Failed to create Keycloak user, status: " + response.getStatusInfo().getReasonPhrase());
        }
    }

    public void resetPassword(String userId, String password) {
        org.keycloak.representations.idm.CredentialRepresentation cred = new org.keycloak.representations.idm.CredentialRepresentation();
        cred.setType(org.keycloak.representations.idm.CredentialRepresentation.PASSWORD);
        cred.setValue(password);
        cred.setTemporary(false);
        realmResource().users().get(userId).resetPassword(cred);
    }

    public Optional<String> findUserIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        String normalizedUsername = username.trim();
        return realmResource().users().searchByUsername(normalizedUsername, true)
                .stream()
                .filter(user -> user.getUsername() != null)
                .filter(user -> user.getUsername().equalsIgnoreCase(normalizedUsername))
                .map(UserRepresentation::getId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst();
    }

    public void clearRequiredAction(String userId, String requiredAction) {
        if (userId == null || userId.isBlank() || requiredAction == null || requiredAction.isBlank()) {
            return;
        }

        var userResource = realmResource().users().get(userId);
        UserRepresentation representation = userResource.toRepresentation();

        List<String> requiredActions = new ArrayList<>(representation.getRequiredActions() == null
                ? List.of()
                : representation.getRequiredActions());

        boolean changed = requiredActions.removeIf(action -> requiredAction.equalsIgnoreCase(action));
        if (!changed) {
            return;
        }

        representation.setRequiredActions(requiredActions);
        userResource.update(representation);
    }

    public boolean hasRequiredAction(String userId, String requiredAction) {
        if (userId == null || userId.isBlank() || requiredAction == null || requiredAction.isBlank()) {
            return false;
        }

        UserRepresentation representation = realmResource().users().get(userId).toRepresentation();
        List<String> requiredActions = representation.getRequiredActions();
        if (requiredActions == null || requiredActions.isEmpty()) {
            return false;
        }

        return requiredActions.stream().anyMatch(action -> requiredAction.equalsIgnoreCase(action));
    }

    public List<RoleRepresentation> getAllRoles() {
        return realmResource().roles().list();
    }

    private RealmResource realmResource() {
        return keycloak.realm(realm);
    }

    private RoleRepresentation resolveOrCreateRealmRole(
            RealmResource realmResource,
            Map<String, RoleRepresentation> availableRoles,
            String roleName) {
        String normalizedRole = normalizeRole(roleName);
        if (normalizedRole.isBlank()) {
            return null;
        }

        RoleRepresentation existing = availableRoles.get(normalizedRole.toUpperCase(Locale.ROOT));
        if (existing != null) {
            return existing;
        }

        RoleRepresentation newRole = new RoleRepresentation();
        newRole.setName(normalizedRole);
        try {
            realmResource.roles().create(newRole);
        } catch (Exception ex) {
            logger.error("event=KEYCLOAK_ROLE_CREATE_FAILED role={} reason={}", normalizedRole, ex.getMessage(), ex);
        }

        try {
            RoleRepresentation created = realmResource.roles().get(normalizedRole).toRepresentation();
            availableRoles.put(created.getName().toUpperCase(Locale.ROOT), created);
            return created;
        } catch (NotFoundException notFoundException) {
            logger.error("event=KEYCLOAK_ROLE_RESOLVE_FAILED role={} reason=not-found-after-create", normalizedRole);
            return null;
        } catch (Exception ex) {
            logger.error("event=KEYCLOAK_ROLE_RESOLVE_FAILED role={} reason={}", normalizedRole, ex.getMessage(), ex);
            return null;
        }
    }

    private GroupRepresentation createGroup(RealmResource realmResource, String groupName) {
        GroupRepresentation newGroup = new GroupRepresentation();
        newGroup.setName(groupName);

        try {
            realmResource.groups().add(newGroup);
        } catch (Exception ex) {
            logger.error("event=KEYCLOAK_GROUP_CREATE_FAILED group={} reason={}", groupName, ex.getMessage(), ex);
        }

        return findGroupByName(realmResource, groupName)
                .orElseThrow(() -> new IllegalStateException("Unable to create or resolve Keycloak group " + groupName));
    }

    private Optional<GroupRepresentation> findGroupByName(RealmResource realmResource, String groupName) {
        String normalizedGroupName = normalizeGroupName(groupName);
        if (normalizedGroupName.isBlank()) {
            return Optional.empty();
        }

        try {
            return realmResource.groups()
                    .groups(normalizedGroupName, Boolean.TRUE, 0, 20, true)
                    .stream()
                    .filter(group -> group != null && group.getName() != null)
                    .filter(group -> normalizeGroupName(group.getName()).equals(normalizedGroupName))
                    .findFirst();
        } catch (Exception ex) {
            logger.error("event=KEYCLOAK_GROUP_QUERY_FAILED group={} reason={}", normalizedGroupName, ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    private Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }

        return roles.stream()
                .map(this::normalizeRole)
                .filter(role -> !role.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> normalizeGroupNames(Set<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) {
            return Set.of();
        }

        return groupNames.stream()
                .map(this::normalizeGroupName)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeGroupName(String groupName) {
        if (groupName == null) {
            return "";
        }
        return groupName.trim().toLowerCase(Locale.ROOT);
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

    private CredentialRepresentation buildTemporaryPasswordCredential() {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(DEFAULT_TEMPORARY_PASSWORD);
        credential.setTemporary(true);
        return credential;
    }
}
