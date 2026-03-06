package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.pi.remoteflowapplication.application.dto.AuthTokenResponse;
import tn.pi.remoteflowapplication.application.dto.LoginRequest;
import tn.pi.remoteflowapplication.application.dto.RefreshRequest;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakTokenService;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final KeycloakTokenService keycloakTokenService;

    public AuthController(KeycloakTokenService keycloakTokenService) {
        this.keycloakTokenService = keycloakTokenService;
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        logger.info("auth.login.attempt username={}", request.getUsername());
        AuthTokenResponse token = keycloakTokenService.login(request.getUsername(), request.getPassword());
        logger.info("auth.login.success username={} roles={}", request.getUsername(), token.getRoles());
        return token;
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return keycloakTokenService.refresh(request.getRefreshToken());
    }

    @GetMapping("/me")
    public Map<String, Object> currentUser(Authentication authentication) {

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ForbiddenOperationException("Unauthenticated");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("userId", jwt.getSubject());

        String username = jwt.getClaimAsString("preferred_username");
        if (username != null) {
            response.put("username", username);
        }

        String email = jwt.getClaimAsString("email");
        if (email != null) {
            response.put("email", email);
        }

        if (authentication.getAuthorities() != null) {
            response.put("roles", authentication.getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
        }
        return response;
    }
}
