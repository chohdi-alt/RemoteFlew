package tn.pi.remoteflowapplication.application.service;

import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.application.port.out.RoleRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.Role;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.util.List;
import java.util.Optional;

@Service
public class KeycloakUserSyncService {

    private final KeycloakAuthService keycloakAuthService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public KeycloakUserSyncService(KeycloakAuthService keycloakAuthService, UserRepository userRepository,
            RoleRepository roleRepository) {
        this.keycloakAuthService = keycloakAuthService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public void synchronizeUsersAndRoles() {
        synchronizeRoles();
        synchronizeUsers();
    }

    private void synchronizeRoles() {
        List<RoleRepresentation> keycloakRoles = keycloakAuthService.getAllRoles();
        if (keycloakRoles == null || keycloakRoles.isEmpty()) {
            return;
        }
        for (RoleRepresentation kr : keycloakRoles) {
            Optional<Role> existingRole = roleRepository.findByName(kr.getName());
            if (existingRole.isEmpty()) {
                Role role = new Role(kr.getName());
                roleRepository.save(role);
            }
        }
    }

    private void synchronizeUsers() {
        List<UserRepresentation> keycloakUsers = keycloakAuthService.getAllUsers();
        if (keycloakUsers == null || keycloakUsers.isEmpty()) {
            return;
        }
        for (UserRepresentation ku : keycloakUsers) {
            String keycloakId = ku.getId();
            String nom = ku.getLastName() != null ? ku.getLastName() : "Unknown";
            String prenom = ku.getFirstName() != null ? ku.getFirstName() : "Unknown";
            String username = ku.getUsername() != null ? ku.getUsername() : keycloakId;
            boolean actif = ku.isEnabled() != null ? ku.isEnabled() : true;

            User user = userRepository.findByKeycloakId(keycloakId)
                    .or(() -> userRepository.findByUsername(username))
                    .map(existing -> {
                        existing.synchronizeIdentity(keycloakId, username, ku.getEmail(), nom, prenom, actif);
                        return existing;
                    })
                    .orElseGet(() -> new User(keycloakId, ku.getEmail(), nom, prenom, username, actif));

            List<String> userRoles = keycloakAuthService.getRealmRoles(keycloakId);
            if (userRoles != null) {
                user.getRoles().clear();
                for (String roleName : userRoles) {
                    if (roleName.startsWith("ROLE_")) {
                        roleName = roleName.substring(5);
                    }
                    final String safeRoleName = roleName;
                    Role role = roleRepository
                            .findByName(safeRoleName)
                            .orElseGet(() -> roleRepository.save(new Role(safeRoleName)));
                    user.addRole(role);
                }
            }

            userRepository.save(user);
        }
    }
}
