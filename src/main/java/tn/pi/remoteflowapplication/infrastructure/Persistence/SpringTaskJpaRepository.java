package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.pi.remoteflowapplication.domain.entity.TaskEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringTaskJpaRepository extends JpaRepository<TaskEntity, Long> {
    List<TaskEntity> findByTypeAndStatus(String type, String status);

    boolean existsByJobKey(Long jobKey);
    
    Optional<TaskEntity> findByJobKey(Long jobKey);

    Optional<TaskEntity> findByRequestIdAndTypeAndStatus(Long requestId, String type, String status);

    long countByTypeAndStatus(String type, String status);
}
