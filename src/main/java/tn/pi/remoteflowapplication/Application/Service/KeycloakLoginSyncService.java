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

            java.util.Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                java.util.List<String> roles = (java.util.List<String>) realmAccess.get("roles");
                for (String roleName : roles) {
                    if (roleName.startsWith("ROLE_")) {
                        roleName = roleName.substring(5);
                    }
                    final String safeRoleName = roleName;
                    tn.pi.remoteflowapplication.domain.entity.Role role = roleRepository
                            .findByName(safeRoleName)
                            .orElseGet(() -> roleRepository
                                    .save(new tn.pi.remoteflowapplication.domain.entity.Role(safeRoleName)));
                    user.addRole(role);
                }
            }

            userRepository.save(user);
        }
    }
}
