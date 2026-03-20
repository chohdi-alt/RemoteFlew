package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.pi.remoteflowapplication.application.dto.AuthTokenResponse;
import tn.pi.remoteflowapplication.application.dto.ChangePasswordRequest;
import tn.pi.remoteflowapplication.application.dto.ChangePasswordResponse;
import tn.pi.remoteflowapplication.application.dto.LoginRequest;
import tn.pi.remoteflowapplication.application.dto.PasswordUpdateRequiredResponse;
import tn.pi.remoteflowapplication.application.dto.RefreshRequest;
import tn.pi.remoteflowapplication.domain.exception.AuthenticationFailedException;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakTokenService;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String REQUIRED_ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";

    private final KeycloakTokenService keycloakTokenService;
    private final KeycloakAuthService keycloakAuthService;

    public AuthController(KeycloakTokenService keycloakTokenService, KeycloakAuthService keycloakAuthService) {
        this.keycloakTokenService = keycloakTokenService;
        this.keycloakAuthService = keycloakAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        logger.info("event=AUTH_LOGIN_ATTEMPT username={}", request.getUsername());
        try {
            AuthTokenResponse token = keycloakTokenService.login(request.getUsername(), request.getPassword());
            logger.info("event=AUTH_LOGIN_SUCCESS username={} roleCount={}",
                    request.getUsername(),
                    token.getRoles() == null ? 0 : token.getRoles().size());
            return ResponseEntity.ok(token);
        } catch (AuthenticationFailedException ex) {
            logger.warn("event=AUTH_LOGIN_FAILURE username={} errorCode={} message={}",
                    request.getUsername(),
                    ex.getErrorCode(),
                    ex.getMessage());

            if ("PASSWORD_UPDATE_REQUIRED".equalsIgnoreCase(ex.getErrorCode())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        new PasswordUpdateRequiredResponse(
                                "PASSWORD_UPDATE_REQUIRED",
                                request.getUsername()));
            }

            throw ex;
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<ChangePasswordResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        logger.info("event=AUTH_PASSWORD_CHANGE_ATTEMPT username={}", request.username());

        ensureTemporaryPasswordIsValid(request.username(), request.temporaryPassword());

        String userId = keycloakAuthService.findUserIdByUsername(request.username())
                .orElseThrow(() -> new AuthenticationFailedException(
                        "Invalid username or temporary password.",
                        "AUTHENTICATION_FAILED",
                        "invalid_grant",
                        "Temporary password validation failed",
                        HttpStatus.UNAUTHORIZED.value()));

        if (!keycloakAuthService.hasRequiredAction(userId, REQUIRED_ACTION_UPDATE_PASSWORD)) {
            throw new AuthenticationFailedException(
                    "Invalid username or temporary password.",
                    "AUTHENTICATION_FAILED",
                    "invalid_grant",
                    "Temporary password validation failed",
                    HttpStatus.UNAUTHORIZED.value());
        }

        try {
            keycloakAuthService.resetPassword(userId, request.newPassword());
            keycloakAuthService.clearRequiredAction(userId, REQUIRED_ACTION_UPDATE_PASSWORD);
            logger.info("event=AUTH_PASSWORD_CHANGE_SUCCESS username={} userId={}", request.username(), userId);
            return ResponseEntity.ok(new ChangePasswordResponse(true, request.username()));
        } catch (Exception ex) {
            logger.error(
                    "event=AUTH_PASSWORD_CHANGE_FAILED username={} userId={} reason={}",
                    request.username(),
                    userId,
                    ex.getMessage(),
                    ex);
            throw new BusinessException("Unable to change password right now. Please retry.", ex);
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

    private void ensureTemporaryPasswordIsValid(String username, String temporaryPassword) {
        try {
            keycloakTokenService.login(username, temporaryPassword);
            return;
        } catch (AuthenticationFailedException ex) {
            if ("PASSWORD_UPDATE_REQUIRED".equalsIgnoreCase(ex.getErrorCode())) {
                return;
            }
        }

        throw new AuthenticationFailedException(
                "Invalid username or temporary password.",
                "AUTHENTICATION_FAILED",
                "invalid_grant",
                "Temporary password validation failed",
                HttpStatus.UNAUTHORIZED.value());
    }
}
