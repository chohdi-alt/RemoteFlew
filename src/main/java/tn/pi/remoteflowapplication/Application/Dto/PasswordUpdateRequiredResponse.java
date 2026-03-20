package tn.pi.remoteflowapplication.application.dto;

public record PasswordUpdateRequiredResponse(
        String error,
        String username
) {
}
