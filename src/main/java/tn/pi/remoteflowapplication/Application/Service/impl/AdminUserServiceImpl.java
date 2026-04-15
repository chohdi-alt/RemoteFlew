package tn.pi.remoteflowapplication.application.service.impl;

import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.application.dto.AdminUserDTO;
import tn.pi.remoteflowapplication.application.dto.CreateUserRequest;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.service.AccountActivationService;
import tn.pi.remoteflowapplication.application.service.AdminUserService;
import tn.pi.remoteflowapplication.application.service.KeycloakUserService;
import tn.pi.remoteflowapplication.application.service.LoginProtectionService;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.infrastructure.security.ClientIpResolver;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserServiceImpl.class);
    private static final String REQUIRED_ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";

    private final KeycloakAuthService keycloakAuthService;
    private final UserRepository userRepository;
    private final tn.pi.remoteflowapplication.application.service.TeamService teamService;
    private final KeycloakUserService keycloakUserService;
    private final AccountActivationService accountActivationService;
    private final LoginProtectionService loginProtectionService;
    private final ClientIpResolver clientIpResolver;

    public AdminUserServiceImpl(
            KeycloakAuthService keycloakAuthService,
            UserRepository userRepository,
            tn.pi.remoteflowapplication.application.service.TeamService teamService,
            KeycloakUserService keycloakUserService,
            AccountActivationService accountActivationService,
            LoginProtectionService loginProtectionService,
            ClientIpResolver clientIpResolver) {
        this.keycloakAuthService = keycloakAuthService;
        this.userRepository = userRepository;
        this.teamService = teamService;
        this.keycloakUserService = keycloakUserService;
        this.accountActivationService = accountActivationService;
        this.loginProtectionService = loginProtectionService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserDTO> findAll(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(this::toAdminUserDto);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDTO updateRoles(String externalId, Set<String> roles) {
        User user = userRepository.findByKeycloakId(externalId)
                .orElseThrow(() -> new BusinessException("User not found: " + externalId));

        String adminUsername = getAdminUsername();
        String targetUsername = user.getUsername();

        try {
            Set<String> normalizedRoles = normalizeRoles(roles);
            keycloakAuthService.setRealmRoles(externalId, normalizedRoles);

            logger.info("ADMIN_EVENT",
                    kv("event", "ADMIN_ROLE_ASSIGNED"),
                    kv("event_normalized", "admin.role.assigned"),
                    kv("category", "ADMIN"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUsername),
                    kv("targetUser", targetUsername),
                    kv("targetId", externalId),
                    kv("roles", normalizedRoles),
                    kv("ip", getClientIp()),
                    kv("ip_private", isPrivateIp(getClientIp())),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"));

            return toAdminUserDto(user);
        } catch (Exception ex) {
            logger.error("ADMIN_EVENT",
                    kv("event", "ADMIN_ROLE_MODIFICATION_FAILED"),
                    kv("event_normalized", "admin.role.assigned_failed"),
                    kv("category", "ADMIN"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUsername),
                    kv("targetUser", targetUsername),
                    kv("reason", ex.getClass().getSimpleName()),
                    kv("ip", getClientIp()),
                    kv("ip_private", isPrivateIp(getClientIp())),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"));
            throw ex;
        }
    }

    @Override
    @Transactional
    public AdminUserDTO updateActivation(String externalId, boolean active) {
        User user = userRepository.findByKeycloakId(externalId)
                .orElseThrow(() -> new BusinessException("User not found: " + externalId));

        String adminUsername = getAdminUsername();
        String targetUsername = user.getUsername();
        String originalEvent = active ? "ADMIN_USER_ACTIVATED" : "ADMIN_USER_DEACTIVATED";

        try {
            boolean wasActive = user.isActif();
            keycloakAuthService.setEnabled(externalId, active);

            if (active && !wasActive) {
                keycloakAuthService.setTemporaryPassword(externalId, generateTemporaryPassword());
                keycloakAuthService.addRequiredAction(externalId, REQUIRED_ACTION_UPDATE_PASSWORD);
                accountActivationService.issueTokenAndDispatch(user, "ADMIN_REACTIVATION");
                loginProtectionService.clearTracking(user.getUsername());
            } else if (!active) {
                loginProtectionService.clearTracking(user.getUsername());
            }

            user.updateActivation(active);
            User saved = userRepository.save(user);

            logger.warn("ADMIN_EVENT",
                    kv("event", originalEvent),
                    kv("event_normalized", active ? "admin.user.activated" : "admin.user.deactivated"),
                    kv("category", "ADMIN"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUsername),
                    kv("targetUser", targetUsername),
                    kv("targetId", externalId),
                    kv("ip", getClientIp()),
                    kv("ip_private", isPrivateIp(getClientIp())),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"));

            return toAdminUserDto(saved);
        } catch (Exception ex) {
            logger.error("ADMIN_EVENT",
                    kv("event", originalEvent + "_FAILED"),
                    kv("event_normalized", (active ? "admin.user.activated" : "admin.user.deactivated") + "_failed"),
                    kv("category", "ADMIN"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUsername),
                    kv("targetUser", targetUsername),
                    kv("reason", ex.getClass().getSimpleName()),
                    kv("ip", getClientIp()),
                    kv("ip_private", isPrivateIp(getClientIp())),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"));
            throw ex;
        }
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AdminUserDTO createUser(CreateUserRequest request) {
        String adminUsername = getAdminUsername();
        String targetUsername = request.username();

        try {
            User user = keycloakUserService.createUser(request);

            logger.warn("ADMIN_EVENT",
                    kv("event", "ADMIN_USER_CREATED"),
                    kv("event_normalized", "admin.user.created"),
                    kv("category", "ADMIN"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUsername),
                    kv("targetUser", targetUsername),
                    kv("ip", getClientIp()),
                    kv("ip_private", isPrivateIp(getClientIp())),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"));

            return toAdminUserDto(user);
        } catch (Exception ex) {
            logger.error("ADMIN_EVENT",
                    kv("event", "ADMIN_USER_CREATION_FAILED"),
                    kv("event_normalized", "admin.user.created_failed"),
                    kv("category", "ADMIN"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUsername),
                    kv("targetUser", targetUsername),
                    kv("reason", ex.getClass().getSimpleName()),
                    kv("ip", getClientIp()),
                    kv("ip_private", isPrivateIp(getClientIp())),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"));
            throw ex;
        }
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void assignRole(String externalId, Set<String> roles) {
        keycloakAuthService.setRealmRoles(externalId, roles);
    }

    @Override
    public void assignTeam(String externalId, Long teamId) {
        String adminUsername = getAdminUsername();
        String targetUsername = userRepository.findByKeycloakId(externalId)
                .map(User::getUsername)
                .orElse("unknown");

        try {
            teamService.assignUserToTeam(externalId, teamId);

            logger.info("ADMIN_EVENT",
                    kv("event", "ADMIN_TEAM_ASSIGNED"),
                    kv("event_normalized", "admin.team.assigned"),
                    kv("category", "ADMIN"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUsername),
                    kv("targetUser", targetUsername),
                    kv("targetId", externalId),
                    kv("teamId", teamId),
                    kv("ip", getClientIp()),
                    kv("ip_private", isPrivateIp(getClientIp())),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("severity", "HIGH"));
        } catch (Exception ex) {
            logger.error("ADMIN_EVENT",
                    kv("event", "ADMIN_TEAM_ASSIGNMENT_FAILED"),
                    kv("event_normalized", "admin.team.assigned_failed"),
                    kv("category", "ADMIN"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUsername),
                    kv("targetUser", targetUsername),
                    kv("reason", ex.getClass().getSimpleName()),
                    kv("ip", getClientIp()),
                    kv("ip_private", isPrivateIp(getClientIp())),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"));
            throw ex;
        }
    }

    @Override
    public void setManager(Long teamId, String managerExternalId) {
        String adminUsername = getAdminUsername();
        String targetUsername = userRepository.findByKeycloakId(managerExternalId)
                .map(User::getUsername)
                .orElse("unknown");

        try {
            teamService.setManager(teamId, managerExternalId);

            logger.info("ADMIN_EVENT",
                    kv("event", "ADMIN_MANAGER_SET"),
                    kv("event_normalized", "admin.manager.set"),
                    kv("category", "ADMIN"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUsername),
                    kv("targetUser", targetUsername),
                    kv("targetId", managerExternalId),
                    kv("teamId", teamId),
                    kv("ip", getClientIp()),
                    kv("ip_private", isPrivateIp(getClientIp())),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("severity", "HIGH"));
        } catch (Exception ex) {
            logger.error("ADMIN_EVENT",
                    kv("event", "ADMIN_MANAGER_SET_FAILED"),
                    kv("event_normalized", "admin.manager.set_failed"),
                    kv("category", "ADMIN"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUsername),
                    kv("targetUser", targetUsername),
                    kv("reason", ex.getClass().getSimpleName()),
                    kv("ip", getClientIp()),
                    kv("ip_private", isPrivateIp(getClientIp())),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"));
            throw ex;
        }
    }

    @Override
    public void syncUsersFromKeycloak() {
        long startTime = System.currentTimeMillis();
        int createdCount = 0;
        int updatedCount = 0;
        int reactivatedCount = 0;
        int deactivatedCount = 0;

        String ip = getClientIp();
        boolean ipPrivate = isPrivateIp(ip);
        String adminUser = getAdminUsername();
        String traceId = getTraceId();
        String operationId = traceId;

        logger.warn("ADMIN_EVENT",
                kv("event", "ADMIN_KEYCLOAK_SYNC_STARTED"),
                kv("event_normalized", "admin.keycloak.sync.started"),
                kv("category", "ADMIN"),
                kv("outcome", "ATTEMPT"),
                kv("severity", "HIGH"),
                kv("adminUser", adminUser),
                kv("ip", ip),
                kv("ip_private", ipPrivate),
                kv("traceId", traceId),
                kv("operationId", operationId),
                kv("source", "remoteflow-backend"),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"));

        try {
            List<UserRepresentation> keycloakUsers = keycloakAuthService.getAllUsers();
            for (UserRepresentation keycloakUser : keycloakUsers) {
                String keycloakId = keycloakUser.getId();
                String email = keycloakUser.getEmail();
                String nom = keycloakUser.getLastName() != null ? keycloakUser.getLastName() : "Unknown";
                String prenom = keycloakUser.getFirstName() != null ? keycloakUser.getFirstName() : "Unknown";
                String username = keycloakUser.getUsername() != null ? keycloakUser.getUsername() : keycloakId;
                boolean active = keycloakUser.isEnabled() == null || keycloakUser.isEnabled();

                final int[] localState = { 0, 0, 0, 0 }; // [created, updated, reactivated, deactivated]
                userRepository.findByKeycloakId(keycloakId).ifPresentOrElse(
                        existing -> {
                            boolean wasActive = existing.isActif();
                            existing.synchronizeIdentity(keycloakId, username, email, nom, prenom, active);
                            userRepository.save(existing);

                            localState[1] = 1; // updated
                            if (!wasActive && active)
                                localState[2] = 1; // reactivated
                            if (wasActive && !active)
                                localState[3] = 1; // deactivated
                        },
                        () -> {
                            User user = new User(keycloakId, email, nom, prenom, username, active);
                            userRepository.save(user);
                            localState[0] = 1; // created
                        });

                createdCount += localState[0];
                updatedCount += localState[1];
                reactivatedCount += localState[2];
                deactivatedCount += localState[3];
            }

            long duration = System.currentTimeMillis() - startTime;
            int affectedTotal = createdCount + updatedCount + reactivatedCount + deactivatedCount;

            logger.info("ADMIN_EVENT",
                    kv("event", "ADMIN_KEYCLOAK_SYNC_COMPLETED"),
                    kv("event_normalized", "admin.keycloak.sync.completed"),
                    kv("category", "ADMIN"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUser),
                    kv("createdCount", createdCount),
                    kv("updatedCount", updatedCount),
                    kv("reactivatedCount", reactivatedCount),
                    kv("deactivatedCount", deactivatedCount),
                    kv("affectedTotal", affectedTotal),
                    kv("operationId", operationId),
                    kv("result_state", "FULL"),
                    kv("durationMs", duration),
                    kv("ip", ip),
                    kv("ip_private", ipPrivate),
                    kv("traceId", traceId),
                    kv("source", "remoteflow-backend"),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"));

            if (reactivatedCount > 0) {
                logger.warn("ADMIN_EVENT",
                        kv("event", "ADMIN_REACTIVATION_DETECTED"),
                        kv("event_normalized", "admin.reactivation.detected"),
                        kv("category", "ADMIN"),
                        kv("outcome", "WARNING"),
                        kv("severity", "HIGH"),
                        kv("adminUser", adminUser),
                        kv("reactivatedCount", reactivatedCount),
                        kv("affectedTotal", affectedTotal),
                        kv("operationId", operationId),
                        kv("ip", ip),
                        kv("ip_private", ipPrivate),
                        kv("traceId", traceId),
                        kv("source", "remoteflow-backend"),
                        kv("connection_type", "HTTP"),
                        kv("layer", "APPLICATION"));
            }

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            int affectedTotal = createdCount + updatedCount + reactivatedCount + deactivatedCount;

            logger.error("ADMIN_EVENT",
                    kv("event", "ADMIN_KEYCLOAK_SYNC_FAILED"),
                    kv("event_normalized", "admin.keycloak.sync.failed"),
                    kv("category", "ADMIN"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "HIGH"),
                    kv("adminUser", adminUser),
                    kv("createdCount", createdCount),
                    kv("updatedCount", updatedCount),
                    kv("reactivatedCount", reactivatedCount),
                    kv("deactivatedCount", deactivatedCount),
                    kv("affectedTotal", affectedTotal),
                    kv("operationId", operationId),
                    kv("result_state", "PARTIAL"),
                    kv("durationMs", duration),
                    kv("ip", ip),
                    kv("ip_private", ipPrivate),
                    kv("traceId", traceId),
                    kv("source", "remoteflow-backend"),
                    kv("error", ex.getClass().getSimpleName()),
                    kv("error_message", ex.getMessage()));
            throw ex;
        }
    }

    private AdminUserDTO toAdminUserDto(User user) {
        Set<String> roles = resolveUserRoles(user);
        String teamName = user.getEquipe() != null ? user.getEquipe().getNom() : null;
        return new AdminUserDTO(
                user.getKeycloakId(),
                user.getFullName(),
                user.getEmail(),
                user.getMatricule(),
                user.isActif(),
                roles,
                teamName);
    }

    private Set<String> resolveUserRoles(User user) {
        Set<String> localRoles = normalizeRoles(user.getRoles()
                .stream()
                .map(role -> role == null ? null : role.getName())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));

        try {
            List<String> keycloakRoles = keycloakAuthService.getRealmRoles(user.getKeycloakId());
            if (keycloakRoles == null || keycloakRoles.isEmpty()) {
                return localRoles;
            }
            return normalizeRoles(new LinkedHashSet<>(keycloakRoles));
        } catch (Exception ex) {
            logger.warn(
                    "event=ADMIN_USER_ROLE_LOOKUP_FALLBACK externalId={} reason={} usingLocalRoles={}",
                    user.getKeycloakId(),
                    ex.getMessage(),
                    !localRoles.isEmpty());
            return localRoles;
        }
    }

    private Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String value = role.trim().toUpperCase(Locale.ROOT);
            if (value.startsWith("ROLE_")) {
                value = value.substring(5);
            }
            normalized.add(value);
        }
        return normalized;
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs == null)
            return "unknown";

        HttpServletRequest request = attrs.getRequest();
        return clientIpResolver.resolve(request);
    }

    private String getTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null) ? traceId : "N/A";
    }

    private String getAdminUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "system";
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null || "unknown".equalsIgnoreCase(ip))
            return false;
        return ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    private String generateTemporaryPassword() {
        return UUID.randomUUID().toString() + "A!";
    }
}
