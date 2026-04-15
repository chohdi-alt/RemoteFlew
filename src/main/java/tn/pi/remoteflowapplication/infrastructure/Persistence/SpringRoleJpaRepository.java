package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.pi.remoteflowapplication.domain.entity.Role;
import java.util.Optional;

public interface SpringRoleJpaRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}
