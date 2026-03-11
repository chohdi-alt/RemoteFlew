package tn.pi.remoteflowapplication.application.port.out;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.pi.remoteflowapplication.domain.entity.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByExternalId(String externalId);

    Optional<Long> findTeamIdByExternalId(String externalId);

    Page<User> findAll(Pageable pageable);

    java.util.List<User> findAll();

    java.util.List<User> findByRoles_Name(String roleName);

    java.util.List<User> findByEquipe_Id(Long teamId);

    User save(User user);
}
