
package tn.pi.remoteflowapplication.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import org.keycloak.representations.idm.UserRepresentation;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final KeycloakAuthService keycloakAuthService;
    private final UserRepository userRepository;

    public AdminController(KeycloakAuthService keycloakAuthService, UserRepository userRepository) {
        this.keycloakAuthService = keycloakAuthService;
        this.userRepository = userRepository;
    }

    @PostMapping("/users/sync")
    public void syncUsersFromKeycloak() {
        List<UserRepresentation> keycloakUsers = keycloakAuthService.getAllUsers();
        for (UserRepresentation kr : keycloakUsers) {
            userRepository.findByExternalId(kr.getId()).ifPresentOrElse(
                    existing -> {
                        // Optional: update existing user if needed
                    },
                    () -> {
                        String fullName = (kr.getFirstName() != null ? kr.getFirstName() : "") + " "
                                + (kr.getLastName() != null ? kr.getLastName() : "");
                        User newUser = new User(kr.getId(), fullName.trim(), kr.getEmail());
                        userRepository.save(newUser);
                    });
        }
    }

    @PostMapping("/rules/reload")
    public void reloadBusinessRules() {
        // future: dynamic rules
    }

    @PostMapping("/workflow/redeploy")
    public void redeployWorkflow() {
        // future: Camunda redeploy
    }
}
