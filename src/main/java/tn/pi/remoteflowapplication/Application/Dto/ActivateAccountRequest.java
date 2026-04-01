package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateAccountRequest(
        @NotBlank(message = "token is required") String token,
        @NotBlank(message = "newPassword is required")
        @Size(min = 8, message = "newPassword must contain at least 8 characters")
        String newPassword
) {
}
