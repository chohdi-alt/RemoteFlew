package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateUserRolesRequest(
        @NotNull @NotEmpty Set<String> roles
) {
}

