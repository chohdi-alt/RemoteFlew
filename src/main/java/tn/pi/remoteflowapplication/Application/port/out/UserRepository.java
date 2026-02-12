package tn.pi.remoteflowapplication.application.port.out;

import tn.pi.remoteflowapplication.domain.entity.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByExternalId(String externalId);

    User save(User user);
}
