package tn.pi.remoteflowapplication.application.port.out;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.pi.remoteflowapplication.domain.entity.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByExternalId(String externalId);

    Optional<Long> findTeamIdByExternalId(String externalId);

    Page<User> findAll(Pageable pageable);

    User save(User user);
}
