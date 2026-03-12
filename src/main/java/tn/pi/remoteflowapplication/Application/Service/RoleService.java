package tn.pi.remoteflowapplication.application.service;

import org.keycloak.representations.idm.RoleRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.RoleDTO;
import tn.pi.remoteflowapplication.application.port.out.RoleRepository;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private static final Logger logger = LoggerFactory.getLogger(RoleService.class);

    private final KeycloakAuthService keycloakAuthService;
    private final RoleRepository roleRepository;

    public RoleService(KeycloakAuthService keycloakAuthService, RoleRepository roleRepository) {
        this.keycloakAuthService = keycloakAuthService;
        this.roleRepository = roleRepository;
    }

    public List<RoleDTO> getRoles() {
        try {
            List<RoleRepresentation> roles = keycloakAuthService.getAllRoles();
            if (roles == null || roles.isEmpty()) {
                return fallbackToLocalRoles();
            }
            return roles.stream()
                    .map(role -> new RoleDTO(role.getName()))
                    .toList();
        } catch (Exception ex) {
            logger.warn("event=ROLE_DIRECTORY_FALLBACK reason={}", ex.getMessage());
            return fallbackToLocalRoles();
        }
    }

    private List<RoleDTO> fallbackToLocalRoles() {
        Set<String> roleNames = roleRepository.findAll()
                .stream()
                .map(role -> role == null ? null : role.getName())
                .filter(name -> name != null && !name.isBlank())
                .map(name -> {
                    String normalized = name.trim().toUpperCase(Locale.ROOT);
                    return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
                })
                .collect(Collectors.toCollection(java.util.TreeSet::new));

        return roleNames.stream()
                .map(RoleDTO::new)
                .toList();
    }
}
