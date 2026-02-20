package tn.pi.remoteflowapplication.application.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.pi.remoteflowapplication.application.dto.AdminUserDTO;

import java.util.Set;

public interface AdminUserService {
    Page<AdminUserDTO> findAll(Pageable pageable);

    AdminUserDTO updateRoles(String externalId, Set<String> roles);

    AdminUserDTO updateActivation(String externalId, boolean active);

    void syncUsersFromKeycloak();
}

