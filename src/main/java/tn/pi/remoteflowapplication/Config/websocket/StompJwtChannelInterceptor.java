package tn.pi.remoteflowapplication.config.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "remoteflow.websocket.enabled", havingValue = "true")
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(StompJwtChannelInterceptor.class);

    private static final String USER_PRIVATE_QUEUE = "/user/queue/signals";
    private static final String LEGACY_MANAGER_TOPIC = "/topic/roles/MANAGER";
    private static final String LEGACY_HR_TOPIC = "/topic/roles/HR";

    private static final Set<String> USER_SUBSCRIBE_SCOPES = Set.of(
            "websocket:subscribe",
            "websocket.subscribe",
            "realtime.read",
            "notifications.read",
            "realtime.user.read",
            "websocket.user.read",
            "user.read",
            "openid",
            "profile",
            "email");

    private static final Set<String> MANAGER_SUBSCRIBE_SCOPES = Set.of(
            "websocket:subscribe",
            "websocket.subscribe",
            "realtime.read",
            "notifications.read",
            "realtime.manager.read",
            "websocket.manager.read",
            "manager.read",
            "openid",
            "profile",
            "email");

    private static final Set<String> HR_SUBSCRIBE_SCOPES = Set.of(
            "websocket:subscribe",
            "websocket.subscribe",
            "realtime.read",
            "notifications.read",
            "realtime.hr.read",
            "websocket.hr.read",
            "hr.read",
            "openid",
            "profile",
            "email");

    private static final Set<String> REQUEST_SUBSCRIBE_SCOPES = Set.of(
            "websocket:subscribe",
            "websocket.subscribe",
            "realtime.read",
            "notifications.read",
            "realtime.requests.read",
            "websocket.requests.read",
            "requests.read",
            "openid",
            "profile",
            "email");

    private static final Set<String> ADMIN_SUBSCRIBE_SCOPES = Set.of(
            "websocket:subscribe",
            "websocket.subscribe",
            "realtime.read",
            "notifications.read",
            "realtime.admin.read",
            "websocket.admin.read",
            "admin.read",
            "openid",
            "profile",
            "email");

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    public StompJwtChannelInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                authenticateConnect(accessor);
                validateConnect(accessor);
            }
            if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                validateSubscribe(accessor);
            }
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        List<String> authorization = accessor.getNativeHeader("Authorization");
        if (authorization != null && !authorization.isEmpty()) {
            String token = authorization.get(0).replace("Bearer ", "");
            try {
                Jwt jwt = jwtDecoder.decode(token);
                Authentication auth = jwtAuthenticationConverter.convert(jwt);
                if (auth != null) {
                    accessor.setUser(auth);
                }
            } catch (Exception e) {
                // Let security filter block unauthorized
            }
        }
    }

    private void validateConnect(StompHeaderAccessor accessor) {
        Authentication auth = toAuthentication(accessor.getUser());
        if (auth == null || !auth.isAuthenticated()) {
            String sessionId = accessor.getSessionId() != null ? accessor.getSessionId() : "unknown";
            String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : "N/A";
            String ip = resolveIp(accessor);

            logger.warn("WS_SECURITY_EVENT",
                    kv("event", "WS_CONNECT_FAILURE"),
                    kv("event_normalized", "ws.connect.failure"),
                    kv("category", "WEBSOCKET"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "MEDIUM"),
                    kv("user", "anonymous"),
                    kv("sessionId", sessionId),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("traceId", traceId),
                    kv("source", "remoteflow-backend"),
                    kv("connection_type", "WEBSOCKET"),
                    kv("layer", "REALTIME"));
            throw new AccessDeniedException("Authentication required for websocket connect");
        }
    }

    private void validateSubscribe(StompHeaderAccessor accessor) {
        Authentication auth = toAuthentication(accessor.getUser());
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required for subscription");
        }
        String username = auth.getName();
        Collection<? extends GrantedAuthority> roles = auth.getAuthorities();

        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Authentication required for subscription");
        }
        if (roles == null || roles.isEmpty()) {
            throw new AccessDeniedException("Authentication required for subscription");
        }
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("Unauthorized subscription");
        }
        if (containsWildcard(destination)) {
            throw new AccessDeniedException("Wildcard subscriptions are not allowed");
        }

        if (hasRole(roles, "ADMIN")) {
            requireAnyScope(roles, ADMIN_SUBSCRIBE_SCOPES, "Insufficient scope for admin subscription");
            return;
        }

        if (USER_PRIVATE_QUEUE.equals(destination)) {
            requireAnyRole(roles, Set.of("USER", "EMPLOYEE", "MANAGER", "HR", "ADMIN"), "Unauthorized subscription");
            requireAnyScope(roles, USER_SUBSCRIBE_SCOPES, "Insufficient scope for private queue subscription");
            return;
        }

        if (LEGACY_MANAGER_TOPIC.equals(destination)) {
            requireRoleAndScope(roles, "MANAGER", MANAGER_SUBSCRIBE_SCOPES, "Unauthorized manager topic");
            return;
        }

        if (LEGACY_HR_TOPIC.equals(destination)) {
            requireRoleAndScope(roles, "HR", HR_SUBSCRIBE_SCOPES, "HR only");
            return;
        }

        if (destination.startsWith("/topic/user/")) {
            requireAnyRole(roles, Set.of("USER", "EMPLOYEE"), "Unauthorized user topic");
            requireAnyScope(roles, USER_SUBSCRIBE_SCOPES, "Insufficient scope for user topic");

            String userId = extractId(destination, "/topic/user/");
            if (!userId.equals(username)) {
                throw new AccessDeniedException("Unauthorized user topic");
            }
            return;
        }

        if (destination.startsWith("/topic/manager/")) {
            requireRoleAndScope(roles, "MANAGER", MANAGER_SUBSCRIBE_SCOPES, "Unauthorized manager topic");

            String managerId = extractId(destination, "/topic/manager/");
            if (!managerId.equals(username)) {
                throw new AccessDeniedException("Unauthorized manager topic");
            }
            return;
        }

        if (destination.startsWith("/topic/team/")) {
            requireRoleAndScope(roles, "MANAGER", MANAGER_SUBSCRIBE_SCOPES, "Unauthorized manager topic");

            String managerId = extractId(destination, "/topic/team/");
            if (!managerId.equals(username)) {
                throw new AccessDeniedException("Unauthorized manager topic");
            }
            return;
        }

        if (isTopicOrSubtopic(destination, "/topic/hr")) {
            requireRoleAndScope(roles, "HR", HR_SUBSCRIBE_SCOPES, "HR only");
            return;
        }

        if (isTopicOrSubtopic(destination, "/topic/requests")) {
            requireRoleAndScope(roles, "HR", REQUEST_SUBSCRIBE_SCOPES, "HR only");
            return;
        }

        logDeniedSubscription(username, roles, destination, accessor);
        throw new AccessDeniedException("Unauthorized subscription");
    }

    private void logDeniedSubscription(String username,
            Collection<? extends GrantedAuthority> roles,
            String destination,
            StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId() != null ? accessor.getSessionId() : "unknown";
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : "N/A";
        String ip = resolveIp(accessor);

        logger.warn("WS_SECURITY_EVENT",
                kv("event", "WS_SUBSCRIBE_DENIED"),
                kv("event_normalized", "ws.subscribe.denied"),
                kv("category", "WEBSOCKET"),
                kv("outcome", "FAILURE"),
                kv("severity", "MEDIUM"),
                kv("user", username),
                kv("sessionId", sessionId),
                kv("destination", destination),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("traceId", traceId),
                kv("source", "remoteflow-backend"),
                kv("connection_type", "WEBSOCKET"),
                kv("layer", "REALTIME"));
    }

    private String resolveIp(StompHeaderAccessor accessor) {
        if (accessor != null && accessor.getSessionAttributes() != null) {
            Object ip = accessor.getSessionAttributes().get("ip");
            if (ip != null)
                return ip.toString();
        }
        return "unknown";
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null || "unknown".equalsIgnoreCase(ip))
            return false;
        return ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    private Authentication toAuthentication(Object principal) {
        if (principal instanceof Authentication authentication) {
            return authentication;
        }
        return null;
    }

    private boolean containsWildcard(String destination) {
        return destination.contains("*") || destination.contains("#") || destination.contains(">");
    }

    private String extractId(String destination, String prefix) {
        String id = destination.substring(prefix.length());
        if (id.isBlank() || id.contains("/") || id.contains("\\") || id.contains("..")) {
            throw new AccessDeniedException("Unauthorized subscription");
        }
        return id;
    }

    private boolean isTopicOrSubtopic(String destination, String base) {
        return destination.equals(base) || destination.startsWith(base + "/");
    }

    private void requireRoleAndScope(Collection<? extends GrantedAuthority> authorities,
            String role,
            Set<String> requiredScopes,
            String errorMessage) {
        if (!hasRole(authorities, role)) {
            throw new AccessDeniedException(errorMessage);
        }
        requireAnyScope(authorities, requiredScopes, "Insufficient scope for subscription");
    }

    private void requireAnyRole(Collection<? extends GrantedAuthority> authorities,
            Set<String> acceptedRoles,
            String errorMessage) {
        boolean roleMatched = acceptedRoles.stream().anyMatch(role -> hasRole(authorities, role));
        if (!roleMatched) {
            throw new AccessDeniedException(errorMessage);
        }
    }

    private boolean hasRole(Collection<? extends GrantedAuthority> authorities, String role) {
        String normalizedRole = role.startsWith("ROLE_")
                ? role.toUpperCase(Locale.ROOT)
                : ("ROLE_" + role).toUpperCase(Locale.ROOT);
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(authority -> authority.toUpperCase(Locale.ROOT))
                .anyMatch(normalizedRole::equals);
    }

    private void requireAnyScope(Collection<? extends GrantedAuthority> authorities,
            Set<String> requiredScopes,
            String errorMessage) {
        Set<String> normalizedScopes = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(authority -> authority.regionMatches(true, 0, "SCOPE_", 0, 6))
                .map(authority -> authority.substring(6).toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        boolean authorized = requiredScopes.stream()
                .map(scope -> scope.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedScopes::contains);

        if (!authorized) {
            throw new AccessDeniedException(errorMessage);
        }
    }
}
