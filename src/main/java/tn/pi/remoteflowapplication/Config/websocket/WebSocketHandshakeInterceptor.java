package tn.pi.remoteflowapplication.config.websocket;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Interceptor to capture client IP during WebSocket handshake.
 * Relies on Spring's normalized remote address (after forward-headers-strategy).
 */
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String ip = "unknown";

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            if (httpRequest != null) {
                // Returns normalized IP (Client IP if behind a trusted proxy)
                ip = normalizeIp(httpRequest.getRemoteAddr());
            }
        }

        attributes.put("ip", ip);
        attributes.put("initial_ip", ip);
        return true;
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) return "unknown";

        // IPv6 loopback → IPv4 loopback
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }

        // Optional: strip IPv6 prefix for IPv4-mapped addresses (::ffff:1.2.3.4)
        if (ip.startsWith("::ffff:")) {
            return ip.substring(7);
        }

        return ip;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No-op
    }
}
