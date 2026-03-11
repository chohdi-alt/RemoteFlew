package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import tn.pi.remoteflowapplication.domain.entity.Team;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import java.util.Optional;
import java.util.List;

@Repository
public class JpaTeamRepository implements TeamRepository {

    private final SpringTeamJpaRepository jpaRepository;

    public JpaTeamRepository(SpringTeamJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Team> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Team> findByName(String name) {
        return jpaRepository.findByNom(name);
    }

    @Override
    public List<Team> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Team save(Team team) {
        return jpaRepository.save(team);
    }
}
