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

import java.util.List;

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
    public void assignUserToTeam(String externalId, Long teamId) {
        User user = userRepository.findByExternalId(externalId)
                .orElseThrow(() -> new BusinessException("User not found: " + externalId));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("Team not found"));

        user.assignTeam(team);
        userRepository.save(user);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void setManager(Long teamId, String managerExternalId) {
        User manager = userRepository.findByExternalId(managerExternalId)
                .orElseThrow(() -> new BusinessException("Manager user not found: " + managerExternalId));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("Team not found"));

        List<String> realmRoles = keycloakAuthService.getRealmRoles(managerExternalId);
        if (realmRoles == null || (!realmRoles.contains("MANAGER") && !realmRoles.contains("ROLE_MANAGER"))) {
            throw new BusinessException("User does not have ROLE_MANAGER");
        }

        manager.assignTeam(team);
        userRepository.save(manager);

        team.setManager(manager);
        teamRepository.save(team);
    }
}
