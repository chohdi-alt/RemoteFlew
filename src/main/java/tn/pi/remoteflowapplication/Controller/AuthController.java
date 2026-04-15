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
import tn.pi.remoteflowapplication.application.dto.ActivateAccountRequest;
import tn.pi.remoteflowapplication.application.dto.ActivateAccountResponse;
import tn.pi.remoteflowapplication.application.dto.AuthTokenResponse;
import tn.pi.remoteflowapplication.application.dto.ChangePasswordRequest;
import tn.pi.remoteflowapplication.application.dto.ChangePasswordResponse;
import tn.pi.remoteflowapplication.application.dto.LoginRequest;
import tn.pi.remoteflowapplication.application.dto.PasswordUpdateRequiredResponse;
import tn.pi.remoteflowapplication.application.dto.RefreshRequest;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.service.ActivationTokenService;
import tn.pi.remoteflowapplication.application.service.LoginProtectionService;
import tn.pi.remoteflowapplication.domain.exception.AuthErrorCode;
import tn.pi.remoteflowapplication.domain.exception.AuthenticationFailedException;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakTokenService;
import tn.pi.remoteflowapplication.infrastructure.security.ClientIpResolver;

import static net.logstash.logback.argument.StructuredArguments.kv;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

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
    private final ActivationTokenService activationTokenService;
    private final UserRepository userRepository;
    private final LoginProtectionService loginProtectionService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(
            KeycloakTokenService keycloakTokenService,
            KeycloakAuthService keycloakAuthService,
            ActivationTokenService activationTokenService,
            UserRepository userRepository,
            LoginProtectionService loginProtectionService,
            ClientIpResolver clientIpResolver) {
        this.keycloakTokenService = keycloakTokenService;
        this.keycloakAuthService = keycloakAuthService;
        this.activationTokenService = activationTokenService;
        this.userRepository = userRepository;
        this.loginProtectionService = loginProtectionService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        HttpServletRequest httpRequest = getRequest();
        String ip = clientIpResolver.resolve(httpRequest);

        logger.info("AUTH_EVENT",
                kv("event", "AUTH_LOGIN_ATTEMPT"),
                kv("event_normalized", "auth.login.attempt"),
                kv("category", "AUTH"),
                kv("outcome", "ATTEMPT"),
                kv("severity", "LOW"),
                kv("user", request.getUsername()),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("traceId", getTraceId()),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"),
                kv("source", "remoteflow-backend"));

        try {
            AuthTokenResponse token = keycloakTokenService.login(request.getUsername(), request.getPassword());
            loginProtectionService.onLoginSuccess(request.getUsername());

            logger.info("AUTH_EVENT",
                    kv("event", "AUTH_LOGIN_SUCCESS"),
                    kv("event_normalized", "auth.login.success"),
                    kv("category", "AUTH"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "LOW"),
                    kv("user", request.getUsername()),
                    kv("roleCount", token.getRoles() == null ? 0 : token.getRoles().size()),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"));

            return ResponseEntity.ok(token);
        } catch (AuthenticationFailedException ex) {
            loginProtectionService.onLoginFailure(request.getUsername(), ex);

            logger.warn("AUTH_EVENT",
                    kv("event", "AUTH_LOGIN_FAILURE"),
                    kv("event_normalized", "auth.login.failure"),
                    kv("category", "AUTH"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "MEDIUM"),
                    kv("user", request.getUsername()),
                    kv("errorCode", ex.getErrorCode()),
                    kv("error_message", ex.getMessage()),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", getTraceId()),
                    kv("source", "remoteflow-backend"),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"));

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
        String ip = clientIpResolver.resolve(getRequest());
        logger.info("AUTH_EVENT",
                kv("event", "AUTH_PASSWORD_CHANGE_ATTEMPT"),
                kv("event_normalized", "auth.password.change.attempt"),
                kv("category", "AUTH"),
                kv("outcome", "ATTEMPT"),
                kv("severity", "LOW"),
                kv("user", request.username()),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("traceId", getTraceId()),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"),
                kv("source", "remoteflow-backend"));

        ensureTemporaryPasswordIsValid(request.username(), request.temporaryPassword());

        String userId = keycloakAuthService.findUserIdByUsername(request.username())
                .orElseThrow(() -> new AuthenticationFailedException(
                        AuthErrorCode.AUTH_INVALID,
                        "Invalid username or temporary password.",
                        "invalid_grant",
                        "Temporary password validation failed",
                        HttpStatus.UNAUTHORIZED.value()));

        if (!keycloakAuthService.hasRequiredAction(userId, REQUIRED_ACTION_UPDATE_PASSWORD)) {
            throw new AuthenticationFailedException(
                    AuthErrorCode.AUTH_INVALID,
                    "Invalid username or temporary password.",
                    "invalid_grant",
                    "Temporary password validation failed",
                    HttpStatus.UNAUTHORIZED.value());
        }

        try {
            keycloakAuthService.resetPassword(userId, request.newPassword());
            keycloakAuthService.clearRequiredAction(userId, REQUIRED_ACTION_UPDATE_PASSWORD);
            logger.info("AUTH_EVENT",
                    kv("event", "AUTH_PASSWORD_CHANGE_SUCCESS"),
                    kv("event_normalized", "auth.password.change.success"),
                    kv("category", "AUTH"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "LOW"),
                    kv("user", request.username()),
                    kv("userId", userId),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", getTraceId()),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));
            return ResponseEntity.ok(new ChangePasswordResponse(true, request.username()));
        } catch (Exception ex) {
            logger.error("AUTH_EVENT",
                    kv("event", "AUTH_PASSWORD_CHANGE_FAILED"),
                    kv("event_normalized", "auth.password.change.failed"),
                    kv("category", "AUTH"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "MEDIUM"),
                    kv("user", request.username()),
                    kv("userId", userId),
                    kv("reason", ex.getMessage()),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", getTraceId()),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));
            throw new BusinessException("Unable to change password right now. Please retry.", ex);
        }
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        String ip = clientIpResolver.resolve(getRequest());
        logger.info("AUTH_EVENT",
                kv("event", "TOKEN_REFRESH_ATTEMPT"),
                kv("event_normalized", "auth.token.refresh.attempt"),
                kv("category", "AUTH"),
                kv("outcome", "ATTEMPT"),
                kv("severity", "LOW"),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("traceId", getTraceId()),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"),
                kv("source", "remoteflow-backend"));
        try {
            AuthTokenResponse token = keycloakTokenService.refresh(request.getRefreshToken());
            logger.info("AUTH_EVENT",
                    kv("event", "TOKEN_REFRESH_SUCCESS"),
                    kv("event_normalized", "auth.token.refresh.success"),
                    kv("category", "AUTH"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "LOW"),
                    kv("roleCount", token.getRoles() == null ? 0 : token.getRoles().size()),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", getTraceId()),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));
            return token;
        } catch (AuthenticationFailedException ex) {
            logger.warn("AUTH_EVENT",
                    kv("event", "TOKEN_REFRESH_FAILURE"),
                    kv("event_normalized", "auth.token.refresh.failed"),
                    kv("category", "AUTH"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "MEDIUM"),
                    kv("errorCode", ex.getErrorCode()),
                    kv("error_message", ex.getMessage()),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", getTraceId()),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));
            throw ex;
        }
    }

    @PostMapping("/activate")
    public ResponseEntity<ActivateAccountResponse> activate(@Valid @RequestBody ActivateAccountRequest request) {
        var activationToken = activationTokenService.findUsableToken(request.token())
                .orElseThrow(() -> new AuthenticationFailedException(
                        "Activation token is invalid or expired.",
                        "ACTIVATION_TOKEN_INVALID",
                        null,
                        null,
                        HttpStatus.UNAUTHORIZED.value()));

        try {
            String keycloakUserId = activationToken.getKeycloakUserId();
            keycloakAuthService.resetPassword(keycloakUserId, request.newPassword());
            keycloakAuthService.clearRequiredAction(keycloakUserId, REQUIRED_ACTION_UPDATE_PASSWORD);
            keycloakAuthService.setEnabled(keycloakUserId, true);

            activationTokenService.consumeToken(request.token())
                    .orElseThrow(() -> new BusinessException("Activation token could not be consumed."));

            userRepository.findByKeycloakId(keycloakUserId).ifPresent(user -> {
                user.updateActivation(true);
                userRepository.save(user);
            });

            String ip = clientIpResolver.resolve(getRequest());
            logger.info("AUTH_EVENT",
                    kv("event", "AUTH_ACTIVATION_SUCCESS"),
                    kv("event_normalized", "auth.activation.success"),
                    kv("category", "AUTH"),
                    kv("outcome", "SUCCESS"),
                    kv("severity", "LOW"),
                    kv("user", activationToken.getUsername()),
                    kv("keycloakUserId", keycloakUserId),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", getTraceId()),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));
            return ResponseEntity.ok(new ActivateAccountResponse(true, activationToken.getUsername()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            String ip = clientIpResolver.resolve(getRequest());
            logger.error("AUTH_EVENT",
                    kv("event", "AUTH_ACTIVATION_FAILED"),
                    kv("event_normalized", "auth.activation.failed"),
                    kv("category", "AUTH"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "MEDIUM"),
                    kv("user", activationToken.getUsername()),
                    kv("reason", ex.getMessage()),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", getTraceId()),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));
            throw new BusinessException("Unable to activate account right now. Please retry.", ex);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        String user = (authentication != null) ? authentication.getName() : "anonymous";
        String ip = clientIpResolver.resolve(getRequest());
        String traceId = getTraceId();

        logger.info("AUTH_EVENT",
                kv("event", "AUTH_LOGOUT"),
                kv("event_normalized", "auth.logout"),
                kv("category", "AUTH"),
                kv("outcome", "SUCCESS"),
                kv("severity", "LOW"),
                kv("user", user),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("traceId", traceId),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"),
                kv("source", "remoteflow-backend"));

        return ResponseEntity.ok().build();
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
                AuthErrorCode.AUTH_INVALID,
                "Invalid username or temporary password.",
                "invalid_grant",
                "Temporary password validation failed",
                HttpStatus.UNAUTHORIZED.value());
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null) ? traceId : "N/A";
    }

    private boolean isPrivateIp(String ip) {
        return ip != null && (ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"));
    }
}
