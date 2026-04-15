package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.pi.remoteflowapplication.domain.entity.Team;
import java.util.Optional;

public interface SpringTeamJpaRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByNom(String nom);

    @org.springframework.data.jpa.repository.Query("select t from Team t left join fetch t.manager left join fetch t.utilisateurs")
    java.util.List<Team> findAllFetched();

    @org.springframework.data.jpa.repository.Query("select t.id from Team t where t.manager.username = :username")
    java.util.List<Long> findManagedTeamIdsByUsername(@org.springframework.data.repository.query.Param("username") String username);
}
