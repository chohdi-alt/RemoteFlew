package tn.pi.remoteflowapplication.infrastructure.security;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
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
import tn.pi.remoteflowapplication.domain.exception.ExternalServiceException;
import tn.pi.remoteflowapplication.domain.exception.KeycloakConflictException;

@Service
public class KeycloakAuthService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakAuthService.class);
    private static final int MAX_LOGGED_BODY_LENGTH = 512;
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
        try {
            return realmResource()
                    .users()
                    .get(userId)
                    .roles()
                    .realmLevel()
                    .listEffective()
                    .stream()
                    .map(RoleRepresentation::getName)
                    .toList();
        } catch (Exception ex) {
            throw mapToExternalServiceException(
                    "read realm roles for user " + userId,
                    buildUserRealmRolesEndpoint(userId),
                    ex);
        }
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
        try {
            return realmResource()
                    .users()
                    .list();
        } catch (Exception ex) {
            throw mapToExternalServiceException("read users", buildUsersEndpoint(), ex);
        }
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

        String endpoint = buildUsersEndpoint();
        logger.info(
                "event=KEYCLOAK_CREATE_REQUEST username={} email={} firstNameSet={} lastNameSet={} url={}",
                username,
                email,
                firstName != null && !firstName.isBlank(),
                lastName != null && !lastName.isBlank(),
                endpoint);

        try (Response response = realmResource().users().create(user)) {
            int status = response.getStatus();
            String responseBody = safeReadResponseBody(response);
            logger.info(
                    "event=KEYCLOAK_CREATE_RESPONSE status={} url={} username={} email={} body={}",
                    status,
                    endpoint,
                    username,
                    email,
                    abbreviateBody(responseBody));

            if (status == 201) {
                String userIdFromLocation = extractUserIdFromLocation(response);
                if (userIdFromLocation != null && !userIdFromLocation.isBlank()) {
                    return userIdFromLocation;
                }

                logger.warn(
                        "event=KEYCLOAK_CREATE_LOCATION_MISSING username={} email={} status={} url={} body={}",
                        username,
                        email,
                        status,
                        endpoint,
                        abbreviateBody(responseBody));

                Optional<String> resolvedUserId = resolveCreatedUserId(username, email);
                if (resolvedUserId.isPresent()) {
                    return resolvedUserId.get();
                }

                throw new ExternalServiceException(
                        "Keycloak returned 201 but no user id could be resolved from Location/search.",
                        status,
                        responseBody,
                        endpoint,
                        "create user");
            }
            if (status == 409) {
                throw new KeycloakConflictException(
                        "User already exists in Keycloak for username '" + username + "' or email '" + email + "'.");
            }
            String reason = response.getStatusInfo() == null
                    ? "unknown"
                    : response.getStatusInfo().getReasonPhrase();
            throw new ExternalServiceException(
                    buildStatusMessage("create user", status, reason),
                    status,
                    responseBody,
                    endpoint,
                    "create user");
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw mapToExternalServiceException("create user", endpoint, ex);
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
        try {
            return realmResource().users().searchByUsername(normalizedUsername, true)
                    .stream()
                    .filter(user -> user.getUsername() != null)
                    .filter(user -> user.getUsername().equalsIgnoreCase(normalizedUsername))
                    .map(UserRepresentation::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .findFirst();
        } catch (Exception ex) {
            throw mapToExternalServiceException(
                    "search user by username '" + normalizedUsername + "'",
                    buildUsersSearchByUsernameEndpoint(normalizedUsername),
                    ex);
        }
    }

    public Optional<String> findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        String normalizedEmail = email.trim();
        try {
            return realmResource().users().searchByEmail(normalizedEmail, true)
                    .stream()
                    .filter(user -> user.getEmail() != null)
                    .filter(user -> user.getEmail().equalsIgnoreCase(normalizedEmail))
                    .map(UserRepresentation::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .findFirst();
        } catch (Exception ex) {
            throw mapToExternalServiceException(
                    "search user by email '" + normalizedEmail + "'",
                    buildUsersSearchByEmailEndpoint(normalizedEmail),
                    ex);
        }
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

    public boolean hasValidCredentialsState(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }

        var userResource = realmResource().users().get(userId);
        UserRepresentation representation = userResource.toRepresentation();
        if (representation == null || representation.isEnabled() == null || !representation.isEnabled()) {
            return false;
        }

        List<String> requiredActions = representation.getRequiredActions();
        if (requiredActions != null && !requiredActions.isEmpty()) {
            return false;
        }

        try {
            return userResource.credentials().stream()
                    .anyMatch(credential -> credential != null
                            && org.keycloak.representations.idm.CredentialRepresentation.PASSWORD
                            .equalsIgnoreCase(credential.getType()));
        } catch (Exception ex) {
            logger.warn("event=KEYCLOAK_CREDENTIAL_STATE_CHECK_FAILED userId={} reason={}", userId, ex.getMessage());
            return false;
        }
    }

    public List<RoleRepresentation> getAllRoles() {
        try {
            return realmResource().roles().list();
        } catch (Exception ex) {
            throw mapToExternalServiceException("read roles", buildRolesEndpoint(), ex);
        }
    }

    public boolean realmRoleExists(String roleName) {
        String normalizedRole = normalizeRole(roleName);
        if (normalizedRole.isBlank()) {
            return false;
        }

        try {
            return realmResource().roles().list().stream()
                    .map(RoleRepresentation::getName)
                    .filter(Objects::nonNull)
                    .map(this::normalizeRole)
                    .anyMatch(existingRole -> existingRole.equalsIgnoreCase(normalizedRole));
        } catch (Exception ex) {
            throw mapToExternalServiceException(
                    "verify realm role '" + normalizedRole + "'",
                    buildRolesEndpoint(),
                    ex);
        }
    }

    private Optional<String> resolveCreatedUserId(String username, String email) {
        Optional<String> byUsername = findUserIdByUsername(username);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return findUserIdByEmail(email);
    }

    private String extractUserIdFromLocation(Response response) {
        if (response == null || response.getLocation() == null) {
            return null;
        }
        String path = response.getLocation().getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private ExternalServiceException mapToExternalServiceException(String operation, String endpoint, Exception ex) {
        if (ex instanceof ExternalServiceException externalServiceException) {
            return externalServiceException;
        }

        if (ex instanceof WebApplicationException webEx) {
            Response response = webEx.getResponse();
            int status = response == null ? -1 : response.getStatus();
            String body = safeReadResponseBody(response);
            String reason = response != null && response.getStatusInfo() != null
                    ? response.getStatusInfo().getReasonPhrase()
                    : "unknown";
            String message = buildStatusMessage(operation, status, reason);

            logger.error(
                    "event=KEYCLOAK_API_ERROR operation={} status={} reason={} url={} body={} cause={}",
                    operation,
                    status,
                    reason,
                    endpoint,
                    abbreviateBody(body),
                    webEx.getMessage(),
                    webEx);

            return new ExternalServiceException(message, webEx, status, body, endpoint, operation);
        }

        if (isNetworkFailure(ex)) {
            logger.error(
                    "event=KEYCLOAK_API_ERROR operation={} status=network_failure url={} cause={}",
                    operation,
                    endpoint,
                    ex.getMessage(),
                    ex);
            return new ExternalServiceException(
                    "Unable to reach Keycloak while attempting to " + operation + ".",
                    ex,
                    null,
                    null,
                    endpoint,
                    operation);
        }

        logger.error(
                "event=KEYCLOAK_API_ERROR operation={} status=unexpected_exception url={} cause={}",
                operation,
                endpoint,
                ex.getMessage(),
                ex);
        return new ExternalServiceException(
                "Keycloak call failed while attempting to " + operation + ".",
                ex,
                null,
                null,
                endpoint,
                operation);
    }

    private String safeReadResponseBody(Response response) {
        if (response == null || !response.hasEntity()) {
            return "";
        }
        try {
            String body = response.readEntity(String.class);
            return body == null ? "" : body;
        } catch (Exception ignored) {
            return "<unavailable>";
        }
    }

    private String buildStatusMessage(String operation, int status, String reason) {
        if (status == 401 || status == 403) {
            return "Keycloak rejected the request to " + operation
                    + " (HTTP " + status + "). Verify admin credentials and realm-management permissions.";
        }
        if (status == 404) {
            return "Keycloak endpoint/realm not found while attempting to " + operation
                    + " (HTTP 404). Verify server URL and realm configuration.";
        }
        if (status == 400) {
            return "Keycloak rejected the request payload while attempting to " + operation
                    + " (HTTP 400).";
        }
        if (status >= 500 && status < 600) {
            return "Keycloak server error while attempting to " + operation + " (HTTP " + status + ").";
        }
        return "Keycloak call failed while attempting to " + operation + " with status "
                + status + " (" + reason + ").";
    }

    private boolean isNetworkFailure(Exception ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof ProcessingException) {
                return true;
            }
            if (cursor instanceof java.net.ConnectException
                    || cursor instanceof java.net.SocketTimeoutException
                    || cursor instanceof java.net.UnknownHostException
                    || cursor instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private String abbreviateBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_LOGGED_BODY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_LOGGED_BODY_LENGTH) + "...";
    }

    private String buildUsersEndpoint() {
        return "/admin/realms/" + realm + "/users";
    }

    private String buildUsersSearchByUsernameEndpoint(String username) {
        return buildUsersEndpoint() + "?username=" + username + "&exact=true";
    }

    private String buildUsersSearchByEmailEndpoint(String email) {
        return buildUsersEndpoint() + "?email=" + email + "&exact=true";
    }

    private String buildRolesEndpoint() {
        return "/admin/realms/" + realm + "/roles";
    }

    private String buildUserRealmRolesEndpoint(String userId) {
        return "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm/composite";
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
}
