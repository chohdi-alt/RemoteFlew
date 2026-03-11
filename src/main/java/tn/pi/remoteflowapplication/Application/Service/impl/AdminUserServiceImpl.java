package tn.pi.remoteflowapplication.application.service.impl;

import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import tn.pi.remoteflowapplication.application.dto.AdminUserDTO;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.service.AdminUserService;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final KeycloakAuthService keycloakAuthService;
    private final UserRepository userRepository;
    private final tn.pi.remoteflowapplication.application.service.TeamService teamService;

    public AdminUserServiceImpl(
            KeycloakAuthService keycloakAuthService,
            UserRepository userRepository,
            tn.pi.remoteflowapplication.application.service.TeamService teamService) {
        this.keycloakAuthService = keycloakAuthService;
        this.userRepository = userRepository;
        this.teamService = teamService;
    }

    @Override
    public Page<AdminUserDTO> findAll(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(this::toAdminUserDto);
    }

    @Override
    public AdminUserDTO updateRoles(String externalId, Set<String> roles) {
        User user = userRepository.findByExternalId(externalId)
                .orElseThrow(() -> new BusinessException("User not found: " + externalId));

        Set<String> normalizedRoles = normalizeRoles(roles);
        keycloakAuthService.setRealmRoles(externalId, normalizedRoles);

        return toAdminUserDto(user);
    }

    @Override
    public AdminUserDTO updateActivation(String externalId, boolean active) {
        User user = userRepository.findByExternalId(externalId)
                .orElseThrow(() -> new BusinessException("User not found: " + externalId));

        keycloakAuthService.setEnabled(externalId, active);
        user.updateActivation(active);
        User saved = userRepository.save(user);

        return toAdminUserDto(saved);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public User createUser(String username, String email, String firstName, String lastName, String password,
            Set<String> roles) {
        String keycloakUserId = keycloakAuthService.createUser(username, email, firstName, lastName);

        keycloakAuthService.resetPassword(keycloakUserId, password);
        keycloakAuthService.setRealmRoles(keycloakUserId, roles);

        User newUser = new User(keycloakUserId, email, lastName, firstName, username, true);
        return userRepository.save(newUser);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void assignRole(String externalId, Set<String> roles) {
        keycloakAuthService.setRealmRoles(externalId, roles);
    }

    @Override
    public void assignTeam(String externalId, Long teamId) {
        teamService.assignUserToTeam(externalId, teamId);
    }

    @Override
    public void setManager(Long teamId, String managerExternalId) {
        teamService.setManager(teamId, managerExternalId);
    }

    @Override
    public void syncUsersFromKeycloak() {
        List<UserRepresentation> keycloakUsers = keycloakAuthService.getAllUsers();
        for (UserRepresentation keycloakUser : keycloakUsers) {
            String externalId = keycloakUser.getId();
            String email = keycloakUser.getEmail();
            String nom = keycloakUser.getLastName() != null ? keycloakUser.getLastName() : "Unknown";
            String prenom = keycloakUser.getFirstName() != null ? keycloakUser.getFirstName() : "Unknown";
            String matricule = keycloakUser.getUsername() != null ? keycloakUser.getUsername() : externalId;
            boolean active = keycloakUser.isEnabled() == null || keycloakUser.isEnabled();

            userRepository.findByExternalId(externalId).ifPresentOrElse(
                    existing -> {
                        existing.synchronizeIdentity(email, nom, prenom, matricule, active);
                        userRepository.save(existing);
                    },
                    () -> {
                        User user = new User(externalId, email, nom, prenom, matricule, active);
                        userRepository.save(user);
                    });
        }
    }

    private AdminUserDTO toAdminUserDto(User user) {
        Set<String> roles = new LinkedHashSet<>(keycloakAuthService.getRealmRoles(user.getExternalId()));
        return new AdminUserDTO(
                user.getExternalId(),
                user.getFullName(),
                user.getEmail(),
                user.isActif(),
                roles);
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
}
