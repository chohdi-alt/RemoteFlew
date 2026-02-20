package tn.pi.remoteflowapplication.application.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import tn.pi.remoteflowapplication.application.dto.PendingValidationTaskDTO;

public interface ValidationInboxService {
    Page<PendingValidationTaskDTO> getPendingValidations(Authentication authentication, Pageable pageable);
}

