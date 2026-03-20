package tn.pi.remoteflowapplication.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.application.dto.TeamDirectoryDTO;
import tn.pi.remoteflowapplication.application.dto.UserDirectoryDTO;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.Role;
import tn.pi.remoteflowapplication.domain.entity.Team;
import tn.pi.remoteflowapplication.domain.entity.User;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DirectoryService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public DirectoryService(UserRepository userRepository, TeamRepository teamRepository) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<UserDirectoryDTO> getAllUsers() {
        return userRepository.findAllWithRolesAndTeam().stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDirectoryDTO> getManagers() {
        return userRepository.findByRoleNameWithFetch("MANAGER").stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDirectoryDTO> getHr() {
        return userRepository.findByRoleNameWithFetch("HR").stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserDirectoryDTO> getTeamMembers(Long teamId) {
        return userRepository.findByTeamIdWithFetch(teamId).stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TeamDirectoryDTO> getAllTeams() {
        return teamRepository.findAllFetched().stream()
                .map(this::mapToTeamDTO)
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
