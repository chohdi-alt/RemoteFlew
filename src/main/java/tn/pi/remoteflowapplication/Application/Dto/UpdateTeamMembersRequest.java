package tn.pi.remoteflowapplication.application.dto;

import java.util.List;

public record UpdateTeamMembersRequest(
        List<String> userExternalIds
) {
}
