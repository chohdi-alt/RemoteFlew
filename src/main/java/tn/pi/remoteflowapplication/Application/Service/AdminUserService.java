package tn.pi.remoteflowapplication.application.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.pi.remoteflowapplication.application.dto.AdminUserDTO;
import tn.pi.remoteflowapplication.domain.entity.User;

import java.util.Set;

public interface AdminUserService {
    Page<AdminUserDTO> findAll(Pageable pageable);

    AdminUserDTO updateRoles(String externalId, Set<String> roles);

    AdminUserDTO updateActivation(String externalId, boolean active);

    void syncUsersFromKeycloak();

    User createUser(String username, String email, String firstName, String lastName, String password,
            Set<String> roles);

    void assignRole(String externalId, Set<String> roles);

    void assignTeam(String externalId, Long teamId);

    void setManager(Long teamId, String managerExternalId);
}
