package tn.pi.remoteflowapplication.application.service;

import org.springframework.security.core.Authentication;
import tn.pi.remoteflowapplication.application.dto.AgreementFileDTO;

public interface AgreementService {
    AgreementFileDTO downloadAgreementPdf(Long requestId, Authentication authentication);
}

