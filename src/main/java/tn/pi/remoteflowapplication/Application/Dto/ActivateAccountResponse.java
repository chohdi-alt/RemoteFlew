package tn.pi.remoteflowapplication.application.dto;

public record ActivateAccountResponse(
        boolean success,
        String username
) {
}
