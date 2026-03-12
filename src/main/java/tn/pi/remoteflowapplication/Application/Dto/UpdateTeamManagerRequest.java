package tn.pi.remoteflowapplication.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTeamManagerRequest(
        @NotBlank String managerExternalId
) {
}
