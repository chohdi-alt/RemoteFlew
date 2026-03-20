package tn.pi.remoteflowapplication.application.service;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.application.port.out.RoleRepository;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.Role;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CurrentUserResolverService {

    private static final String DEFAULT_ROLE = "EMPLOYEE";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TeleworkRequestRepository teleworkRequestRepository;

    public CurrentUserResolverService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            TeleworkRequestRepository teleworkRequestRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.teleworkRequestRepository = teleworkRequestRepository;
    }

    @Transactional
    public User resolveCurrentUser(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException("Missing JWT context.");
        }

        String keycloakId = normalizeClaim(jwt.getSubject());
        if (keycloakId == null) {
            throw new BusinessException("JWT subject (sub) is missing.");
        }

        String resolvedUsername = normalizeClaim(jwt.getClaimAsString("preferred_username"));
        if (resolvedUsername == null) {
            resolvedUsername = keycloakId;
        }
        final String username = resolvedUsername;

        String email = normalizeClaim(jwt.getClaimAsString("email"));
        String fullName = resolveDisplayName(jwt, username);

        User user = userRepository.findByKeycloakId(keycloakId)
                .or(() -> userRepository.findByUsername(username))
                .map(existing -> synchronizeExistingUser(existing, keycloakId, username, email, fullName))
                .orElseGet(() -> new User(keycloakId, username, fullName, email));

        synchronizeRoles(user, jwt);

        return userRepository.save(user);
    }

    private User synchronizeExistingUser(
            User existing,
            String keycloakId,
            String username,
            String email,
            String fullName) {
        String previousUsername = existing.getUsername();
        existing.synchronizeJwtProfile(keycloakId, username, email, fullName, true);
        teleworkRequestRepository.reassignEmployeeId(previousUsername, username);
        return existing;
    }

    private void synchronizeRoles(User user, Jwt jwt) {
        Set<String> roleNames = extractRoleNames(jwt);

        if (roleNames.isEmpty()) {
            if (user.getRoles().isEmpty()) {
                user.addRole(resolveRole(DEFAULT_ROLE));
            }
            return;
        }

        user.getRoles().clear();
        for (String roleName : roleNames) {
            user.addRole(resolveRole(roleName));
        }
    }

    private Role resolveRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
    }

    private Set<String> extractRoleNames(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();

        Object realmAccessRaw = jwt.getClaim("realm_access");
        if (realmAccessRaw instanceof Map<?, ?> realmAccess) {
            Object realmRolesRaw = realmAccess.get("roles");
            if (realmRolesRaw instanceof List<?> realmRoles) {
                for (Object roleRaw : realmRoles) {
                    if (roleRaw instanceof String roleName && !roleName.isBlank()) {
                        roles.add(normalizeRoleName(roleName));
                    }
                }
            }
        }

        Object resourceAccessRaw = jwt.getClaim("resource_access");
        if (resourceAccessRaw instanceof Map<?, ?> resourceAccess) {
            for (Object clientAccessRaw : resourceAccess.values()) {
                if (!(clientAccessRaw instanceof Map<?, ?> clientAccess)) {
                    continue;
                }
                Object clientRolesRaw = clientAccess.get("roles");
                if (!(clientRolesRaw instanceof List<?> clientRoles)) {
                    continue;
                }
                for (Object roleRaw : clientRoles) {
                    if (roleRaw instanceof String roleName && !roleName.isBlank()) {
                        roles.add(normalizeRoleName(roleName));
                    }
                }
            }
        }

        return roles;
    }

    private String normalizeRoleName(String roleName) {
        String normalized = roleName.trim();
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }

    private String resolveDisplayName(Jwt jwt, String fallbackUsername) {
        String explicitName = normalizeClaim(jwt.getClaimAsString("name"));
        if (explicitName != null) {
            return explicitName;
        }

        String givenName = normalizeClaim(jwt.getClaimAsString("given_name"));
        String familyName = normalizeClaim(jwt.getClaimAsString("family_name"));

        if (givenName != null && familyName != null) {
            return givenName + " " + familyName;
        }
        if (givenName != null) {
            return givenName;
        }
        if (familyName != null) {
            return familyName;
        }
        return fallbackUsername;
    }

    private String normalizeClaim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
