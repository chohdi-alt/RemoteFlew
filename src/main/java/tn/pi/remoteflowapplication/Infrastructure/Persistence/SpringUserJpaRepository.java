package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.pi.remoteflowapplication.domain.entity.User;
import java.util.Optional;

public interface SpringUserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByExternalId(String externalId);

    @Query("select u.equipe.id from User u where u.externalId = :externalId")
    Optional<Long> findTeamIdByExternalId(@Param("externalId") String externalId);
}
