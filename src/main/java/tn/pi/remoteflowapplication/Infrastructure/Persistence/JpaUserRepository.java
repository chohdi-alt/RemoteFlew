package tn.pi.remoteflowapplication.infrastructure.persistence;

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
    public User save(User user) {
        return jpaRepository.save(user);
    }
}
