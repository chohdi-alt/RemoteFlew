package tn.pi.remoteflowapplication.application.service.impl;

import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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

    public AdminUserServiceImpl(
            KeycloakAuthService keycloakAuthService,
            UserRepository userRepository) {
        this.keycloakAuthService = keycloakAuthService;
        this.userRepository = userRepository;
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
    public void syncUsersFromKeycloak() {
        List<UserRepresentation> keycloakUsers = keycloakAuthService.getAllUsers();
        for (UserRepresentation keycloakUser : keycloakUsers) {
            String externalId = keycloakUser.getId();
            String fullName = buildFullName(keycloakUser);
            String email = keycloakUser.getEmail();
            boolean active = keycloakUser.isEnabled() == null || keycloakUser.isEnabled();

            userRepository.findByExternalId(externalId).ifPresentOrElse(
                    existing -> {
                        existing.synchronizeProfile(fullName, email, active);
                        userRepository.save(existing);
                    },
                    () -> {
                        User user = new User(externalId, fullName, email);
                        user.updateActivation(active);
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

    private String buildFullName(UserRepresentation user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? "Unknown User" : fullName;
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

