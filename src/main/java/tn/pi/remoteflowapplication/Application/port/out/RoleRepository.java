package tn.pi.remoteflowapplication.application.port.out;

import tn.pi.remoteflowapplication.domain.entity.Role;
import java.util.Optional;
import java.util.List;

public interface RoleRepository {
    Optional<Role> findByName(String name);

    List<Role> findAll();

    Role save(Role role);
}
