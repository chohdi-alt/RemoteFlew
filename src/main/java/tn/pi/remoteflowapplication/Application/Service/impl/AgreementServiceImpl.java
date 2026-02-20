package tn.pi.remoteflowapplication.application.service.impl;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.AgreementFileDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.service.AgreementService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;

@Service
public class AgreementServiceImpl implements AgreementService {

    private final TeleworkRequestRepository teleworkRequestRepository;
    private final DocumentStoragePort documentStoragePort;

    public AgreementServiceImpl(
            TeleworkRequestRepository teleworkRequestRepository,
            DocumentStoragePort documentStoragePort) {
        this.teleworkRequestRepository = teleworkRequestRepository;
        this.documentStoragePort = documentStoragePort;
    }

    @Override
    public AgreementFileDTO downloadAgreementPdf(Long requestId, Authentication authentication) {
        TeleworkRequest request = teleworkRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        if (hasRole(authentication, "ROLE_EMPLOYEE")
                && !hasAnyRole(authentication, "ROLE_MANAGER", "ROLE_HR", "ROLE_ADMIN")
                && !request.getEmployeeId().equals(authentication.getName())) {
            throw new BusinessException("Vous ne pouvez pas acceder aux accords d'un autre employe");
        }

        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new BusinessException("Agreement PDF is available only for approved requests");
        }

        String agreementNodeId = request.getAgreementNodeId();
        if (agreementNodeId == null || agreementNodeId.isBlank()) {
            agreementNodeId = request.getAlfrescoNodeId();
        }
        if (agreementNodeId == null || agreementNodeId.isBlank()) {
            throw new BusinessException("Agreement PDF not found in Alfresco");
        }

        byte[] content = documentStoragePort.download(agreementNodeId);
        if (content == null || content.length == 0) {
            throw new BusinessException("Agreement PDF content is empty");
        }

        return new AgreementFileDTO(
                "agreement-" + requestId + ".pdf",
                content);
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities()
                .stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        if (authentication == null || roles == null) {
            return false;
        }
        java.util.Set<String> roleSet = java.util.Set.of(roles);
        return authentication.getAuthorities()
                .stream()
                .anyMatch(a -> roleSet.contains(a.getAuthority()));
    }
}
