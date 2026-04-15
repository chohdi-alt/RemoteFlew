package tn.pi.remoteflowapplication.config.websocket;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "remoteflow.websocket.enabled", havingValue = "true")
public class WebSocketMetricsConfig {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketMetricsConfig.class);
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public WebSocketMetricsConfig(MeterRegistry meterRegistry) {
        Gauge.builder("websocket.connections.active", activeConnections, AtomicInteger::get)
                .description("Number of active WebSocket connections")
                .register(meterRegistry);
        logger.info("WebSocket metrics tracking initialized");
    }

    @EventListener
    public void onSessionConnect(SessionConnectEvent event) {
        int count = activeConnections.incrementAndGet();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String username = (user != null) ? user.getName() : "anonymous";
        String sessionId = accessor.getSessionId() != null ? accessor.getSessionId() : "unknown";
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : "N/A";
        String ip = resolveIp(accessor);

        logger.info("WS_SECURITY_EVENT",
                kv("event", "WS_CONNECT"),
                kv("event_normalized", "ws.connect"),
                kv("category", "WEBSOCKET"),
                kv("outcome", "ESTABLISHED"),
                kv("user", username),
                kv("sessionId", sessionId),
                kv("traceId", traceId),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("connection_type", "WEBSOCKET"),
                kv("layer", "REALTIME"),
                kv("source", "remoteflow-backend"),
                kv("severity", "LOW"));
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        int count = activeConnections.decrementAndGet();
        logger.debug("WebSocket session disconnected. Active connections: {}", count);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String username = (user != null) ? user.getName() : "anonymous";
        String sessionId = accessor.getSessionId() != null ? accessor.getSessionId() : "unknown";
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : "N/A";
        String ip = resolveIp(accessor);

        boolean ipMissing = "unknown".equals(ip);
        boolean ipPrivate = (ip.startsWith("172.") || ip.startsWith("10.") || ip.startsWith("192.168."));

        if (ipMissing) {
            logger.warn("WS_SECURITY_EVENT",
                    kv("event", "WS_IP_MISSING"),
                    kv("event_normalized", "ws.ip.missing"),
                    kv("category", "WEBSOCKET"),
                    kv("outcome", "ANOMALY"),
                    kv("severity", "HIGH"),
                    kv("user", username),
                    kv("ip", ip),
                    kv("ip_private", isPrivateIp(ip)),
                    kv("sessionId", sessionId),
                    kv("traceId", traceId),
                    kv("connection_type", "WEBSOCKET"),
                    kv("layer", "REALTIME"),
                    kv("source", "remoteflow-backend"));
        }

        CloseStatus status = event.getCloseStatus();
        String closeReason = "TIMEOUT";
        if (status == null) {
            closeReason = "UNKNOWN";
        } else if (CloseStatus.NORMAL.equals(status) || CloseStatus.GOING_AWAY.equals(status)) {
            closeReason = "CLIENT_DISCONNECT";
        } else if (CloseStatus.SESSION_NOT_RELIABLE.equals(status)) {
            closeReason = "ABRUPT";
        }

        String event1 = "WS_DISCONNECT";
        Object initialIp = accessor.getSessionAttributes() != null ? accessor.getSessionAttributes().get("initial_ip")
                : "unknown";

        logger.info("WS_SECURITY_EVENT",
                kv("event", event1),
                kv("event_normalized", "ws.disconnect"),
                kv("category", "WEBSOCKET"),
                kv("outcome", "TERMINATED"),
                kv("user", username),
                kv("sessionId", sessionId),
                kv("closeReason", closeReason),
                kv("traceId", traceId),
                kv("ip", ip),
                kv("initial_ip", initialIp),
                kv("ip_missing", ipMissing),
                kv("ip_private", isPrivateIp(ip)),
                kv("connection_type", "WEBSOCKET"),
                kv("layer", "REALTIME"),
                kv("source", "remoteflow-backend"),
                kv("severity", "LOW"));
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
}
