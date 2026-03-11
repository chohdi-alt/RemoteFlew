package tn.pi.remoteflowapplication.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.pi.remoteflowapplication.application.dto.TeamDirectoryDTO;
import tn.pi.remoteflowapplication.application.dto.UserDirectoryDTO;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.Role;
import tn.pi.remoteflowapplication.domain.entity.Team;
import tn.pi.remoteflowapplication.domain.entity.User;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/directory")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDirectoryController {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public AdminDirectoryController(UserRepository userRepository, TeamRepository teamRepository) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    @GetMapping("/users")
    public List<UserDirectoryDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/teams")
    public List<TeamDirectoryDTO> getAllTeams() {
        return teamRepository.findAll().stream()
                .map(this::mapToTeamDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/managers")
    public List<UserDirectoryDTO> getManagers() {
        return userRepository.findByRoles_Name("MANAGER").stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/hr")
    public List<UserDirectoryDTO> getHr() {
        return userRepository.findByRoles_Name("HR").stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/team/{teamId}/members")
    public List<UserDirectoryDTO> getTeamMembers(@PathVariable Long teamId) {
        return userRepository.findByEquipe_Id(teamId).stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }

    private UserDirectoryDTO mapToUserDTO(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        String teamName = user.getEquipe() != null ? user.getEquipe().getNom() : null;

        return new UserDirectoryDTO(
                user.getId(),
                user.getExternalId(),
                user.getEmail(),
                user.getNom(),
                user.getPrenom(),
                roles,
                teamName);
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
