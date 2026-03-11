package tn.pi.remoteflowapplication.application.port.out;

import tn.pi.remoteflowapplication.domain.entity.Team;
import java.util.Optional;
import java.util.List;

public interface TeamRepository {
    Optional<Team> findById(Long id);

    Optional<Team> findByName(String name);

    List<Team> findAll();

    Team save(Team team);
}
