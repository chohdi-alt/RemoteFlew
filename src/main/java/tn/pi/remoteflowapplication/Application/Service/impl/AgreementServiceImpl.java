package tn.pi.remoteflowapplication.application.service.impl;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.AgreementFileDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.service.AgreementService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;

@Service
public class AgreementServiceImpl implements AgreementService {

    private final TeleworkRequestRepository teleworkRequestRepository;
    private final DocumentStoragePort documentStoragePort;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public AgreementServiceImpl(
            TeleworkRequestRepository teleworkRequestRepository,
            DocumentStoragePort documentStoragePort,
            UserRepository userRepository,
            TeamRepository teamRepository) {
        this.teleworkRequestRepository = teleworkRequestRepository;
        this.documentStoragePort = documentStoragePort;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    @Override
    public AgreementFileDTO downloadAgreementPdf(Long requestId, Authentication authentication) {
        TeleworkRequest request = teleworkRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Request not found"));

        enforceAgreementOwnership(request, authentication);

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

    private void enforceAgreementOwnership(TeleworkRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("Unauthenticated");
        }

        String username = authentication.getName();
        if (hasAnyRole(authentication, "ROLE_ADMIN", "ROLE_HR")) {
            return;
        }

        if (hasRole(authentication, "ROLE_MANAGER")) {
            if (!isUserInManagerScope(request.getEmployeeId(), username)) {
                throw new BusinessException("Manager cannot access this user's agreement");
            }
            return;
        }

        if (hasAnyRole(authentication, "ROLE_USER", "ROLE_EMPLOYEE")) {
            if (!request.getEmployeeId().equals(username)) {
                throw new BusinessException("Vous ne pouvez pas acceder aux accords d'un autre employe");
            }
            return;
        }

        throw new BusinessException("Access denied");
    }

    private boolean isUserInManagerScope(String targetUser, String managerUsername) {
        Long targetTeamId = userRepository.findTeamIdByExternalId(targetUser).orElse(null);
        if (targetTeamId == null) {
            return false;
        }

        boolean managesTargetTeam = teamRepository.findManagedTeamIdsByUsername(managerUsername).stream()
                .anyMatch(targetTeamId::equals);
        if (managesTargetTeam) {
            return true;
        }

        Long managerTeamId = userRepository.findTeamIdByExternalId(managerUsername).orElse(null);
        return managerTeamId != null && managerTeamId.equals(targetTeamId);
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
