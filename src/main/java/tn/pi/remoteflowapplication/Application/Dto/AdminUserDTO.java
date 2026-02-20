package tn.pi.remoteflowapplication.application.dto;

import java.util.Set;

public record AdminUserDTO(
        String externalId,
        String fullName,
        String email,
        boolean active,
        Set<String> roles
) {
}

