package tn.pi.remoteflowapplication.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.CreateUserRequest;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.exception.ExternalServiceException;
import tn.pi.remoteflowapplication.domain.exception.UserAlreadyExistsException;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final UserRepository userRepository;
    private final KeycloakUserOnboardingTransactionService keycloakUserOnboardingTransactionService;
    private final AccountActivationService accountActivationService;

    public KeycloakUserService(
            KeycloakAuthService keycloakAuthService,
            UserRepository userRepository,
            KeycloakUserOnboardingTransactionService keycloakUserOnboardingTransactionService,
            AccountActivationService accountActivationService) {
        this.keycloakAuthService = keycloakAuthService;
        this.userRepository = userRepository;
        this.keycloakUserOnboardingTransactionService = keycloakUserOnboardingTransactionService;
        this.accountActivationService = accountActivationService;
    }

    public User createUser(CreateUserRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new BusinessException("Username is required to create a Keycloak user.");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BusinessException("Email is required for activation-based onboarding.");
        }

        String normalizedUsername = request.username().trim();
        String normalizedEmail = request.email().trim();
        String correlationId = UUID.randomUUID().toString();

        logger.info(
                "event=USER_CREATE_START correlationId={} username={} email={} externalId={}",
                correlationId,
                normalizedUsername,
                normalizedEmail,
                "n/a");

        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new UserAlreadyExistsException("Username already exists: " + normalizedUsername);
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException("Email already exists: " + normalizedEmail);
        }
        logger.info(
                "event=KEYCLOAK_PRECHECK_USERNAME_CALL correlationId={} username={} email={}",
                correlationId,
                normalizedUsername,
                normalizedEmail);
        if (keycloakAuthService.findUserIdByUsername(normalizedUsername).isPresent()) {
            throw new UserAlreadyExistsException("Username already exists in Keycloak: " + normalizedUsername);
        }
        logger.info(
                "event=KEYCLOAK_PRECHECK_EMAIL_CALL correlationId={} username={} email={}",
                correlationId,
                normalizedUsername,
                normalizedEmail);
        if (keycloakAuthService.findUserIdByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException("Email already exists in Keycloak: " + normalizedEmail);
        }

        String normalizedRole = resolvePrimaryRole(request.roles());
        String targetGroup = ROLE_TO_GROUP.get(normalizedRole);

        logger.info(
                "event=KEYCLOAK_CREATE_CALL correlationId={} username={} email={} externalId={}",
                correlationId,
                normalizedUsername,
                normalizedEmail,
                "n/a");
        String keycloakUserId = keycloakAuthService.createUser(
                normalizedUsername,
                request.email(),
                request.firstName(),
                request.lastName());
        logger.info(
                "event=KEYCLOAK_CREATE_SUCCESS correlationId={} username={} email={} externalId={}",
                correlationId,
                normalizedUsername,
                normalizedEmail,
                keycloakUserId);

        try {
            boolean roleExists = keycloakAuthService.realmRoleExists(normalizedRole);
            if (roleExists) {
                keycloakAuthService.setRealmRoles(keycloakUserId, Set.of(normalizedRole));
            } else {
                logger.warn(
                        "event=KEYCLOAK_ROLE_MISSING_SKIP correlationId={} username={} email={} externalId={} role={}",
                        correlationId,
                        normalizedUsername,
                        normalizedEmail,
                        keycloakUserId,
                        normalizedRole);
            }
            keycloakAuthService.assignUserToManagedGroup(keycloakUserId, targetGroup, MANAGED_GROUPS);
        } catch (ExternalServiceException ex) {
            logger.error(
                    "event=KEYCLOAK_USER_ASSIGNMENT_EXTERNAL_FAILURE correlationId={} username={} email={} externalId={} role={} group={} reason={}",
                    correlationId,
                    normalizedUsername,
                    normalizedEmail,
                    keycloakUserId,
                    normalizedRole,
                    targetGroup,
                    ex.getMessage(),
                    ex);
            deleteUserFromKeycloak(keycloakUserId);
            throw ex;
        } catch (Exception ex) {
            logger.error(
                    "event=KEYCLOAK_USER_ASSIGNMENT_FAILED correlationId={} username={} email={} externalId={} role={} group={} reason={}",
                    correlationId,
                    normalizedUsername,
                    normalizedEmail,
                    keycloakUserId,
                    normalizedRole,
                    targetGroup,
                    ex.getMessage(),
                    ex);
            deleteUserFromKeycloak(keycloakUserId);
            throw new ExternalServiceException("User creation failed while assigning role/group in Keycloak.", ex);
        }

        try {
            logger.info(
                    "event=USER_CREATE_DB_PERSIST_START correlationId={} username={} email={} externalId={}",
                    correlationId,
                    normalizedUsername,
                    normalizedEmail,
                    keycloakUserId);
            KeycloakUserOnboardingTransactionService.OnboardingResult onboardingResult =
                    keycloakUserOnboardingTransactionService.synchronizeAndIssueActivationToken(keycloakUserId);
            User user = onboardingResult.user();
            ActivationTokenService.IssuedActivationToken issuedToken = onboardingResult.issuedToken();

            accountActivationService.dispatchActivationEmail(user, issuedToken.rawToken(), "USER_CREATE");
            logger.info(
                    "event=USER_CREATE_SUCCESS correlationId={} username={} email={} externalId={}",
                    correlationId,
                    normalizedUsername,
                    normalizedEmail,
                    keycloakUserId);
            return user;
        } catch (ExternalServiceException ex) {
            logger.error(
                    "event=USER_ONBOARDING_EXTERNAL_FAILURE correlationId={} username={} email={} externalId={} reason={}",
                    correlationId,
                    normalizedUsername,
                    normalizedEmail,
                    keycloakUserId,
                    ex.getMessage(),
                    ex);
            deleteUserFromKeycloak(keycloakUserId);
            throw ex;
        } catch (Exception ex) {
            logger.error(
                    "event=USER_ONBOARDING_SETUP_FAILED correlationId={} username={} email={} externalId={} reason={}",
                    correlationId,
                    normalizedUsername,
                    normalizedEmail,
                    keycloakUserId,
                    ex.getMessage(),
                    ex);
            deleteUserFromKeycloak(keycloakUserId);
            throw new BusinessException("User creation failed while preparing activation.", ex);
        }
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

    private void deleteUserFromKeycloak(String keycloakUserId) {
        logger.info("event=KEYCLOAK_COMPENSATION_DELETE externalId={}", keycloakUserId);
        keycloakAuthService.deleteUser(keycloakUserId);
    }
}
