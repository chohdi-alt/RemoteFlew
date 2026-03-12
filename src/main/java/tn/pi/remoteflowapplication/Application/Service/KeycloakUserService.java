package tn.pi.remoteflowapplication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.application.dto.CreateUserRequest;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class KeycloakUserService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakUserService.class);

    private static final Set<String> SUPPORTED_ROLES = Set.of("EMPLOYEE", "MANAGER", "HR", "ADMIN");
    private static final Map<String, String> ROLE_TO_GROUP = Map.of(
            "EMPLOYEE", "employees",
            "MANAGER", "managers",
            "HR", "hr",
            "ADMIN", "admins");
    private static final Set<String> MANAGED_GROUPS = Set.copyOf(ROLE_TO_GROUP.values());

    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakUserSyncService keycloakUserSyncService;
    private final UserRepository userRepository;

    public KeycloakUserService(
            KeycloakAuthService keycloakAuthService,
            KeycloakUserSyncService keycloakUserSyncService,
            UserRepository userRepository) {
        this.keycloakAuthService = keycloakAuthService;
        this.keycloakUserSyncService = keycloakUserSyncService;
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(CreateUserRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new BusinessException("Username is required to create a Keycloak user.");
        }

        String normalizedRole = resolvePrimaryRole(request.roles());
        String targetGroup = ROLE_TO_GROUP.get(normalizedRole);

        String keycloakUserId = keycloakAuthService.createUser(
                request.username(),
                request.email(),
                request.firstName(),
                request.lastName());

        try {
            keycloakAuthService.setRealmRoles(keycloakUserId, Set.of(normalizedRole));
            keycloakAuthService.assignUserToManagedGroup(keycloakUserId, targetGroup, MANAGED_GROUPS);
        } catch (Exception ex) {
            logger.error(
                    "event=KEYCLOAK_USER_ASSIGNMENT_FAILED username={} userId={} role={} group={} reason={}",
                    request.username(),
                    keycloakUserId,
                    normalizedRole,
                    targetGroup,
                    ex.getMessage(),
                    ex);
            keycloakAuthService.deleteUser(keycloakUserId);
            throw new BusinessException("User creation failed while assigning role/group in Keycloak.", ex);
        }

        keycloakUserSyncService.synchronizeUsersAndRoles();

        return userRepository.findByExternalId(keycloakUserId)
                .orElseThrow(() -> new BusinessException("User synchronization failed for: " + keycloakUserId));
    }

    private String resolvePrimaryRole(Set<String> requestedRoles) {
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            throw new BusinessException("Exactly one role is required: employee, manager, hr, or admin.");
        }

        Set<String> normalizedRoles = requestedRoles.stream()
                .map(this::normalizeRole)
                .filter(role -> !role.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (normalizedRoles.size() != 1) {
            throw new BusinessException("Exactly one role is allowed when creating a user.");
        }

        String role = normalizedRoles.iterator().next();
        if (!SUPPORTED_ROLES.contains(role)) {
            throw new BusinessException("Unsupported role '" + role
                    + "'. Allowed roles are: employee, manager, hr, admin.");
        }

        return role;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }

        String value = role.trim().toUpperCase(Locale.ROOT);
        return value.startsWith("ROLE_") ? value.substring(5) : value;
    }
}
