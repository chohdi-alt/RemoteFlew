package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;

import java.util.List;

public interface SpringTeleworkJpaRepository
        extends JpaRepository<TeleworkRequest, Long> {

    List<TeleworkRequest> findByEmployeeId(String employeeId);
}
