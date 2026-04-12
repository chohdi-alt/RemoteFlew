package tn.pi.remoteflowapplication.application.query;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.AuditHistoryDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkStatusDTO;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

import tn.pi.remoteflowapplication.domain.exception.ResourceNotFoundException;

@Service
public class TeleworkStatusQueryService {

        private final TeleworkRequestRepository repository;
        private final UserRepository userRepository;
        private final TeamRepository teamRepository;
        private final tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort documentStoragePort;

        public TeleworkStatusQueryService(
                        TeleworkRequestRepository repository,
                        UserRepository userRepository,
                        TeamRepository teamRepository,
                        tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort documentStoragePort) {
                this.repository = repository;
                this.userRepository = userRepository;
                this.teamRepository = teamRepository;
                this.documentStoragePort = documentStoragePort;
        }

        public List<TeleworkStatusDTO> findByEmployee(String employeeId) {
                return repository.findByEmployeeId(employeeId)
                                .stream()
                                .map(r -> new TeleworkStatusDTO(
                                                r.getId(),
                                                r.getStartDate(),
                                                r.getEndDate(),
                                                r.getStatus().name(),
                                                r.getDecisionComment().orElse(null),
                                                r.getStatus() == RequestStatus.SPECIAL,
                                                r.getJustificationReason(),
                                                r.getAlfrescoNodeId() != null ? r.getAlfrescoNodeId().replace("workspace://SpacesStore/", "") : null,
                                                r.getManagerComment(),
                                                r.getHrComment()))
                                .collect(Collectors.toList());
        }

        public List<TeleworkStatusDTO> findForCurrentEmployee(String employeeId) {
                return repository.findByEmployeeId(employeeId)
                                .stream()
                                .map(r -> new TeleworkStatusDTO(
                                                r.getId(),
                                                r.getStartDate(),
                                                r.getEndDate(),
                                                r.getStatus().name(),
                                                r.getDecisionComment().orElse(null),
                                                r.getStatus() == RequestStatus.SPECIAL,
                                                r.getJustificationReason(),
                                                r.getAlfrescoNodeId() != null ? r.getAlfrescoNodeId().replace("workspace://SpacesStore/", "") : null,
                                                r.getManagerComment(),
                                                r.getHrComment()))
                                .collect(Collectors.toList());
        }



        public TeleworkStatusDTO findById(Long id, Authentication authentication) {
                TeleworkRequest request = repository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

                enforceRequestOwnership(request, authentication);

                return new TeleworkStatusDTO(
                                request.getId(),
                                request.getStartDate(),
                                request.getEndDate(),
                                request.getStatus().name(),
                                request.getDecisionComment().orElse(null),
                                request.getStatus() == RequestStatus.SPECIAL,
                                request.getJustificationReason(),
                                request.getAlfrescoNodeId() != null ? request.getAlfrescoNodeId().replace("workspace://SpacesStore/", "") : null,
                                request.getManagerComment(),
                                request.getHrComment());
        }

        private void enforceRequestOwnership(TeleworkRequest request, Authentication authentication) {
                if (authentication == null || !authentication.isAuthenticated()) {
                        throw new ForbiddenOperationException("Unauthenticated");
                }

                String username = authentication.getName();
                boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
                boolean isHr = hasRole(authentication, "ROLE_HR");
                boolean isManager = hasRole(authentication, "ROLE_MANAGER");

                // Admin and HR can see everything
                if (isAdmin || isHr) {
                        return;
                }

                // Managers can see requests for their team
                if (isManager) {
                        Long managerTeamId = userRepository.findTeamIdByUsername(username).orElse(null);
                        if (managerTeamId != null && managerTeamId.equals(request.getTeamId())) {
                                return;
                        }
                        // Fall through to check if they are the creator (just in case)
                }

                // Employees can only see their own requests
                if (request.getEmployeeId().equals(username)) {
                        return;
                }

                throw new ForbiddenOperationException("You are not allowed to access this request.");
        }

        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> viewJustificatif(Long id, Authentication authentication) {
                TeleworkRequest request = repository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

                enforceRequestOwnership(request, authentication);

                String nodeId = request.getAlfrescoNodeId();
                if (nodeId == null || nodeId.isBlank()) {
                        throw new tn.pi.remoteflowapplication.domain.exception.BusinessException("Document unavailable");
                }
                String cleanNodeId = nodeId.replace("workspace://SpacesStore/", "");

                try {
                        byte[] fileData = documentStoragePort.download(cleanNodeId);
                        if (fileData == null) {
                                throw new tn.pi.remoteflowapplication.domain.exception.BusinessException("Document unavailable");
                        }
                        
                        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(fileData);
                        
                        return org.springframework.http.ResponseEntity.ok()
                                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                                .header("X-Content-Type-Options", "nosniff")
                                .header("Content-Security-Policy", "default-src 'none';")
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment().filename("justificatif.pdf").build().toString())
                                .contentLength(fileData.length)
                                .body(resource);
                } catch (Exception e) {
                        throw new tn.pi.remoteflowapplication.domain.exception.BusinessException("Document unavailable");
                }
        }

        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public List<AuditHistoryDTO> getRequestHistory(Long requestId, Authentication authentication) {
                var request = repository.findByIdWithAuditLogs(requestId)
                                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
                enforceHistoryOwnership(request, authentication);

                return request.consulterHistorique().stream()
                                .map(log -> new AuditHistoryDTO(
                                                log.getAction(),
                                                log.getEntite(),
                                                log.getTimestamp(),
                                                log.getUtilisateur()))
                                .collect(Collectors.toList());
        }

        private void enforceHistoryOwnership(TeleworkRequest request, Authentication authentication) {
                if (authentication == null || !authentication.isAuthenticated()) {
                        throw new ForbiddenOperationException("Unauthenticated");
                }

                String username = authentication.getName();

                if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_HR")) {
                        return;
                }

                if (hasRole(authentication, "ROLE_MANAGER")) {
                        if (!isUserInManagerScope(request.getEmployeeId(), username)) {
                                throw new ForbiddenOperationException("Manager cannot access this user's history.");
                        }
                        return;
                }

                boolean isUser = hasRole(authentication, "ROLE_USER") || hasRole(authentication, "ROLE_EMPLOYEE");
                if (isUser) {
                        if (!request.getEmployeeId().equals(username)) {
                                throw new ForbiddenOperationException("User cannot access others.");
                        }
                        return;
                }

                throw new ForbiddenOperationException("You are not allowed to access this request history.");
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
                return authentication.getAuthorities().stream()
                                .anyMatch(authority -> role.equals(authority.getAuthority()));
        }
}
