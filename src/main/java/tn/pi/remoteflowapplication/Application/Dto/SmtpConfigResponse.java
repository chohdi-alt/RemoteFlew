package tn.pi.remoteflowapplication.application.dto;

import java.time.Instant;

public record SmtpConfigResponse(
        Long id,
        String name,
        String host,
        Integer port,
        String protocol,
        String username,
        boolean hasPassword,
        String fromEmail,
        boolean authEnabled,
        boolean starttlsEnabled,
        boolean sslEnabled,
        Integer connectionTimeoutMs,
        Integer readTimeoutMs,
        Integer writeTimeoutMs,
        boolean active,
        String source,
        Instant updatedAt
) {
}
