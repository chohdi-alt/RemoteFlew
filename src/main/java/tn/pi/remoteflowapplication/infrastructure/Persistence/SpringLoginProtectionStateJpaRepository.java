package tn.pi.remoteflowapplication.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.pi.remoteflowapplication.domain.entity.LoginProtectionState;

import java.util.Optional;

public interface SpringLoginProtectionStateJpaRepository extends JpaRepository<LoginProtectionState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from LoginProtectionState state where state.username = :username")
    Optional<LoginProtectionState> findByUsernameForUpdate(@Param("username") String username);

    Optional<LoginProtectionState> findByUsername(String username);

    void deleteByUsername(String username);
}
