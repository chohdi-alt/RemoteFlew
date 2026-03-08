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
import tn.pi.remoteflowapplication.domain.exception.AuthenticationFailedException;
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
        logger.info("event=AUTH_LOGIN_ATTEMPT username={}", request.getUsername());
        try {
            AuthTokenResponse token = keycloakTokenService.login(request.getUsername(), request.getPassword());
            logger.info("event=AUTH_LOGIN_SUCCESS username={} roleCount={}",
                    request.getUsername(),
                    token.getRoles() == null ? 0 : token.getRoles().size());
            return token;
        } catch (AuthenticationFailedException ex) {
            logger.warn("event=AUTH_LOGIN_FAILURE username={} errorCode={} message={}",
                    request.getUsername(),
                    ex.getErrorCode(),
                    ex.getMessage());
            throw ex;
        }
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        logger.info("event=TOKEN_REFRESH_ATTEMPT hasRefreshToken={}",
                request.getRefreshToken() != null && !request.getRefreshToken().isBlank());
        try {
            AuthTokenResponse token = keycloakTokenService.refresh(request.getRefreshToken());
            logger.info("event=TOKEN_REFRESH_SUCCESS roleCount={}",
                    token.getRoles() == null ? 0 : token.getRoles().size());
            return token;
        } catch (AuthenticationFailedException ex) {
            logger.warn("event=TOKEN_REFRESH_FAILURE errorCode={} message={}", ex.getErrorCode(), ex.getMessage());
            throw ex;
        }
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
