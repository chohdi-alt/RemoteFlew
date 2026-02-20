package tn.pi.remoteflowapplication.application.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.pi.remoteflowapplication.application.dto.AuditLogDTO;

import java.time.LocalDateTime;

public interface AuditLogQueryService {
    Page<AuditLogDTO> search(
            String action,
            String actor,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);
}

