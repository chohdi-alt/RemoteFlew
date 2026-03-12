package tn.pi.remoteflowapplication.application.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.application.port.out.RoleRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.User;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class KeycloakLoginSyncService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public KeycloakLoginSyncService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @EventListener
    @Transactional
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) event.getAuthentication();
            Jwt jwt = jwtAuth.getToken();

            String sub = jwt.getSubject();
            if (sub == null) {
                return;
            }

            String username = jwt.getClaimAsString("preferred_username");
            String email = jwt.getClaimAsString("email");
            String givenName = jwt.getClaimAsString("given_name");
            String familyName = jwt.getClaimAsString("family_name");

            String nom = familyName != null ? familyName : "Unknown";
            String prenom = givenName != null ? givenName : "Unknown";
            String matricule = username != null ? username : sub;

            User user = userRepository.findByExternalId(sub)
                    .map(existing -> {
                        existing.synchronizeIdentity(email, nom, prenom, matricule, true);
                        return existing;
                    })
                    .orElseGet(() -> new User(sub, email, nom, prenom, matricule, true));

            user.getRoles().clear();

            for (String roleName : extractRoleNames(jwt)) {
                final String safeRoleName = roleName;
                tn.pi.remoteflowapplication.domain.entity.Role role = roleRepository
                        .findByName(safeRoleName)
                        .orElseGet(() -> roleRepository
                                .save(new tn.pi.remoteflowapplication.domain.entity.Role(safeRoleName)));
                user.addRole(role);
            }

            userRepository.save(user);
        }
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
}
