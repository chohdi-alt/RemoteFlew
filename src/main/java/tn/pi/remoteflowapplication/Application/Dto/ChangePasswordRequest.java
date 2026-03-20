package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "temporaryPassword is required") String temporaryPassword,
        @NotBlank(message = "newPassword is required")
        @Size(min = 8, message = "newPassword must contain at least 8 characters")
        String newPassword
) {
}
