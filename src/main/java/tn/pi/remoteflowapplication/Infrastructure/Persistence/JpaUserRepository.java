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
    public Optional<User> findByExternalId(String externalId) {
        return jpaRepository.findByExternalId(externalId);
    }

    @Override
    public Optional<Long> findTeamIdByExternalId(String externalId) {
        return jpaRepository.findTeamIdByExternalId(externalId);
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
}
