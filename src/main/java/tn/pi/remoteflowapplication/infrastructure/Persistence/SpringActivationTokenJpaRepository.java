package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.pi.remoteflowapplication.domain.entity.ActivationToken;
import tn.pi.remoteflowapplication.domain.entity.ActivationTokenStatus;

import java.util.List;
import java.util.Optional;

public interface SpringActivationTokenJpaRepository extends JpaRepository<ActivationToken, Long> {
    Optional<ActivationToken> findByTokenHash(String tokenHash);

    List<ActivationToken> findByKeycloakUserIdAndStatus(String keycloakUserId, ActivationTokenStatus status);
}
