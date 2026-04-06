package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import tn.pi.remoteflowapplication.domain.entity.User;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {

    private final SpringUserJpaRepository jpaRepository;

    public JpaUserRepository(SpringUserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findByKeycloakId(String keycloakId) {
        return jpaRepository.findByKeycloakId(keycloakId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmailIgnoreCase(email);
    }

    @Override
    public Optional<Long> findTeamIdByUsername(String username) {
        return jpaRepository.findTeamIdByUsername(username);
    }

    @Override
    public Optional<Long> findTeamIdByKeycloakId(String keycloakId) {
        return jpaRepository.findTeamIdByKeycloakId(keycloakId);
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable);
    }

    @Override
    public User save(User user) {
        return jpaRepository.save(user);
    }

    @Override
    public java.util.List<User> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public java.util.List<User> findByRoles_Name(String roleName) {
        return jpaRepository.findByRoles_Name(roleName);
    }

    @Override
    public java.util.List<User> findByEquipe_Id(Long teamId) {
        return jpaRepository.findByEquipe_Id(teamId);
    }

    @Override
    public java.util.List<User> findAllWithRolesAndTeam() {
        return jpaRepository.findAllWithRolesAndTeam();
    }

    @Override
    public java.util.List<User> findByRoleNameWithFetch(String roleName) {
        return jpaRepository.findByRoleNameWithFetch(roleName);
    }

    @Override
    public java.util.List<User> findByTeamIdWithFetch(Long teamId) {
        return jpaRepository.findByTeamIdWithFetch(teamId);
    }
}
