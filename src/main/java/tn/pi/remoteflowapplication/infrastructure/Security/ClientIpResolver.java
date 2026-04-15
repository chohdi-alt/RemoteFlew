package tn.pi.remoteflowapplication.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    /**
     * Resolves the real client IP address, handling reverse proxies securely.
     * Only trusts forwarded headers if the request comes from a known internal/trusted network.
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        if (isFromTrustedProxy(request)) {
            // 1. X-Forwarded-For (standard)
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String ip = xff.split(",")[0].trim();
                if (isValidIp(ip)) {
                    return ip;
                }
            }

            // 2. X-Real-IP (fallback)
            String xRealIp = request.getHeader("X-Real-IP");
            if (isValidIp(xRealIp)) {
                return xRealIp;
            }
        }

        // 3. direct fallback (or if proxy is untrusted)
        return request.getRemoteAddr();
    }

    private boolean isFromTrustedProxy(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null) return false;

        return remote.startsWith("127.")
                || remote.startsWith("10.")
                || remote.startsWith("192.168.")
                || (remote.startsWith("172.") && is172Private(remote))
                || remote.equals("0:0:0:0:0:0:0:1")
                || remote.equals("::1");
    }

    private boolean is172Private(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length < 2) return false;
            int secondOctet = Integer.parseInt(parts[1]);
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidIp(String ip) {
        return ip != null && !ip.isBlank() && ip.length() < 50;
    }
}
