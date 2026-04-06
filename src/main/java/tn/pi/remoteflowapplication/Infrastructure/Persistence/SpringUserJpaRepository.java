package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.pi.remoteflowapplication.domain.entity.User;
import java.util.Optional;

public interface SpringUserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    @Query("select distinct u from User u left join fetch u.roles left join fetch u.equipe")
    java.util.List<User> findAllWithRolesAndTeam();

    @Query("select u.equipe.id from User u where u.username = :username")
    Optional<Long> findTeamIdByUsername(@Param("username") String username);

    @Query("select u.equipe.id from User u where u.keycloakId = :keycloakId")
    Optional<Long> findTeamIdByKeycloakId(@Param("keycloakId") String keycloakId);

    @Query("select distinct u from User u left join fetch u.roles left join fetch u.equipe where u.id in (select u2.id from User u2 join u2.roles r2 where r2.name = :roleName)")
    java.util.List<User> findByRoleNameWithFetch(@Param("roleName") String roleName);

    @Query("select distinct u from User u left join fetch u.roles left join fetch u.equipe where u.equipe.id = :teamId")
    java.util.List<User> findByTeamIdWithFetch(@Param("teamId") Long teamId);

    java.util.List<User> findByRoles_Name(String roleName);

    java.util.List<User> findByEquipe_Id(Long teamId);
}
