package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank String username,
        String email,
        String firstName,
        String lastName,
        @NotNull @NotEmpty @Size(min = 1, max = 1) Set<String> roles
) {
}
