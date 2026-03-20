package tn.pi.remoteflowapplication.application.dto;

public record ChangePasswordResponse(
        boolean success,
        String username
) {
}
