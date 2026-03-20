package tn.pi.remoteflowapplication.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.domain.entity.Team;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.infrastructure.security.KeycloakAuthService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final KeycloakAuthService keycloakAuthService;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository,
            KeycloakAuthService keycloakAuthService) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.keycloakAuthService = keycloakAuthService;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Team createTeam(String nom, String code, Integer effectif) {
        Team team = new Team(nom, code, effectif);
        return teamRepository.save(team);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Team createTeam(String name, String managerExternalId) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("Team name is required.");
        }
        teamRepository.findByName(name).ifPresent(existing -> {
            throw new BusinessException("Team already exists: " + name);
        });

        Team team = new Team(name);
        Team saved = teamRepository.save(team);

        if (managerExternalId != null && !managerExternalId.isBlank()) {
            setManager(saved.getId(), managerExternalId);
        }
        return teamRepository.findById(saved.getId()).orElse(saved);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void assignUserToTeam(String externalId, Long teamId) {
        User user = userRepository.findByKeycloakId(externalId)
                .orElseThrow(() -> new BusinessException("User not found: " + externalId));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("Team not found"));

        user.assignTeam(team);
        userRepository.save(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void setManager(Long teamId, String managerExternalId) {
        User manager = userRepository.findByKeycloakId(managerExternalId)
                .orElseThrow(() -> new BusinessException("Manager user not found: " + managerExternalId));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("Team not found"));

        List<String> realmRoles = keycloakAuthService.getRealmRoles(manager.getKeycloakId());
        if (realmRoles == null || (!realmRoles.contains("MANAGER") && !realmRoles.contains("ROLE_MANAGER"))) {
            throw new BusinessException("User does not have ROLE_MANAGER");
        }

        manager.assignTeam(team);
        userRepository.save(manager);

        team.setManager(manager);
        teamRepository.save(team);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateTeamMembers(Long teamId, List<String> userExternalIds) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("Team not found"));

        Set<String> targetIds = new LinkedHashSet<>();
        if (userExternalIds != null) {
            for (String externalId : userExternalIds) {
                if (externalId != null && !externalId.isBlank()) {
                    targetIds.add(externalId.trim());
                }
            }
        }
        if (team.getManager() != null && team.getManager().getExternalId() != null) {
            targetIds.add(team.getManager().getExternalId());
        }

        List<User> currentMembers = userRepository.findByEquipe_Id(teamId);
        for (User member : currentMembers) {
            if (!targetIds.contains(member.getExternalId())) {
                member.assignTeam(null);
                userRepository.save(member);
            }
        }

        for (String externalId : targetIds) {
            User user = userRepository.findByKeycloakId(externalId)
                    .orElseThrow(() -> new BusinessException("User not found: " + externalId));
            if (user.getEquipe() == null || !teamId.equals(user.getEquipe().getId())) {
                user.assignTeam(team);
                userRepository.save(user);
            }
        }
    }
}
