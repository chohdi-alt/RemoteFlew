package tn.pi.remoteflowapplication.application.dto;

public record SmtpConnectionTestResponse(
        boolean success,
        String message
) {
}
