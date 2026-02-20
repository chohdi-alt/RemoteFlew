package tn.pi.remoteflowapplication.application.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.AuditLogDTO;
import tn.pi.remoteflowapplication.application.service.AuditLogQueryService;
import tn.pi.remoteflowapplication.domain.entity.AuditLog;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringAuditLogJpaRepository;

import java.time.LocalDateTime;

@Service
public class AuditLogQueryServiceImpl implements AuditLogQueryService {

    private final SpringAuditLogJpaRepository auditLogRepository;

    public AuditLogQueryServiceImpl(SpringAuditLogJpaRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public Page<AuditLogDTO> search(
            String action,
            String actor,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {

        Specification<AuditLog> specification = Specification.where(null);

        if (action != null && !action.isBlank()) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(cb.lower(root.get("action")), action.toLowerCase()));
        }

        if (actor != null && !actor.isBlank()) {
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("utilisateur")), "%" + actor.toLowerCase() + "%"));
        }

        if (from != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("timestamp"), from));
        }

        if (to != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("timestamp"), to));
        }

        return auditLogRepository.findAll(specification, pageable)
                .map(log -> new AuditLogDTO(
                        log.getId(),
                        log.getAction(),
                        log.getEntite(),
                        log.getTimestamp(),
                        log.getUtilisateur()));
    }
}

