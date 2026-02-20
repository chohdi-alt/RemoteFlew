package tn.pi.remoteflowapplication.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.pi.remoteflowapplication.domain.entity.AuditLog;

public interface SpringAuditLogJpaRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
}

