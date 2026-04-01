package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SmtpConfigRequest(
        @NotBlank String name,
        @NotBlank String host,
        @NotNull @Min(1) @Max(65535) Integer port,
        String protocol,
        String username,
        String password,
        String fromEmail,
        boolean authEnabled,
        boolean starttlsEnabled,
        boolean sslEnabled,
        Integer connectionTimeoutMs,
        Integer readTimeoutMs,
        Integer writeTimeoutMs,
        boolean active
) {
}
