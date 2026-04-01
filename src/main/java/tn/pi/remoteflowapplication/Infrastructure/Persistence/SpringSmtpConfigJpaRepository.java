package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.pi.remoteflowapplication.domain.entity.SmtpConfig;

import java.util.Optional;

public interface SpringSmtpConfigJpaRepository extends JpaRepository<SmtpConfig, Long> {
    Optional<SmtpConfig> findFirstByActiveTrueOrderByUpdatedAtDesc();
}
