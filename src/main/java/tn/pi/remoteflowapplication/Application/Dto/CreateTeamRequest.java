package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTeamRequest(
        @NotBlank String name,
        @NotBlank String managerExternalId
) {
}
