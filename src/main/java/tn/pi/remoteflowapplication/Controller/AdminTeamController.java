package tn.pi.remoteflowapplication.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.pi.remoteflowapplication.application.dto.CreateTeamRequest;
import tn.pi.remoteflowapplication.application.dto.TeamDirectoryDTO;
import tn.pi.remoteflowapplication.application.dto.UpdateTeamManagerRequest;
import tn.pi.remoteflowapplication.application.dto.UpdateTeamMembersRequest;
import tn.pi.remoteflowapplication.application.service.TeamService;
import tn.pi.remoteflowapplication.domain.entity.Team;

@RestController
@RequestMapping("/api/admin/teams")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTeamController {

    private final TeamService teamService;

    public AdminTeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public TeamDirectoryDTO createTeam(@RequestBody @Valid CreateTeamRequest request) {
        Team team = teamService.createTeam(request.name(), request.managerExternalId());
        return mapToTeamDTO(team);
    }

    @PutMapping("/{teamId}/manager")
    public void updateTeamManager(
            @PathVariable Long teamId,
            @RequestBody @Valid UpdateTeamManagerRequest request) {
        teamService.setManager(teamId, request.managerExternalId());
    }

    @PutMapping("/{teamId}/members")
    public void updateTeamMembers(
            @PathVariable Long teamId,
            @RequestBody UpdateTeamMembersRequest request) {
        teamService.updateTeamMembers(teamId, request == null ? null : request.userExternalIds());
    }

    private TeamDirectoryDTO mapToTeamDTO(Team team) {
        String managerName = team.getManager() != null
                ? team.getManager().getNom() + " " + team.getManager().getPrenom()
                : null;

        int membersCount = team.getUtilisateurs() != null ? team.getUtilisateurs().size() : 0;

        return new TeamDirectoryDTO(
                team.getId(),
                team.getNom(),
                managerName,
                membersCount);
    }
}
