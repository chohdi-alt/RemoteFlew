package tn.pi.remoteflowapplication.application.port.out;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.pi.remoteflowapplication.domain.entity.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<Long> findTeamIdByUsername(String username);

    Optional<Long> findTeamIdByKeycloakId(String keycloakId);

    default Optional<User> findByExternalId(String externalId) {
        String candidate = normalizeIdentityValue(externalId);
        if (candidate == null) {
            return Optional.empty();
        }
        return findByKeycloakId(candidate).or(() -> findByUsername(candidate));
    }

    default Optional<Long> findTeamIdByExternalId(String externalId) {
        String candidate = normalizeIdentityValue(externalId);
        if (candidate == null) {
            return Optional.empty();
        }
        return findTeamIdByUsername(candidate).or(() -> findTeamIdByKeycloakId(candidate));
    }

    Page<User> findAll(Pageable pageable);

    java.util.List<User> findAll();

    java.util.List<User> findAllWithRolesAndTeam();

    java.util.List<User> findByRoles_Name(String roleName);

    java.util.List<User> findByRoleNameWithFetch(String roleName);

    java.util.List<User> findByEquipe_Id(Long teamId);

    java.util.List<User> findByTeamIdWithFetch(Long teamId);

    User save(User user);

    private static String normalizeIdentityValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
