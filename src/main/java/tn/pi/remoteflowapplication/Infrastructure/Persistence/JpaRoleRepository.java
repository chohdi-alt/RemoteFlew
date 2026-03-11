package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import tn.pi.remoteflowapplication.domain.entity.Role;
import tn.pi.remoteflowapplication.application.port.out.RoleRepository;
import java.util.Optional;
import java.util.List;

@Repository
public class JpaRoleRepository implements RoleRepository {

    private final SpringRoleJpaRepository jpaRepository;

    public JpaRoleRepository(SpringRoleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Role> findByName(String name) {
        return jpaRepository.findByName(name);
    }

    @Override
    public List<Role> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Role save(Role role) {
        return jpaRepository.save(role);
    }
}
