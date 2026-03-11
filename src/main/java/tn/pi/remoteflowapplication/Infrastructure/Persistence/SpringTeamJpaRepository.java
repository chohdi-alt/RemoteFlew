package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.pi.remoteflowapplication.domain.entity.Team;
import java.util.Optional;

public interface SpringTeamJpaRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByNom(String nom);
}
