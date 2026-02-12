package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.pi.remoteflowapplication.domain.entity.User;
import java.util.Optional;

public interface SpringUserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByExternalId(String externalId);
}
