package tn.pi.remoteflowapplication.application.query;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.AuditHistoryDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkStatusDTO;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.util.List;
import java.util.stream.Collectors;

import tn.pi.remoteflowapplication.domain.exception.ResourceNotFoundException;

@Service
public class TeleworkStatusQueryService {

        private final TeleworkRequestRepository repository;

        public TeleworkStatusQueryService(
                        TeleworkRequestRepository repository) {
                this.repository = repository;
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
                                                r.getStatus() == RequestStatus.SPECIAL))
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
                                                r.getStatus() == RequestStatus.SPECIAL))
                                .collect(Collectors.toList());
        }

        public TeleworkStatusDTO findById(Long id) {
                return repository.findById(id)
                                .map(r -> new TeleworkStatusDTO(
                                                r.getId(),
                                                r.getStartDate(),
                                                r.getEndDate(),
                                                r.getStatus().name(),
                                                r.getDecisionComment().orElse(null),
                                                r.getStatus() == RequestStatus.SPECIAL))
                                .orElse(null);
        }

        public List<AuditHistoryDTO> getRequestHistory(Long requestId, Authentication authentication) {
                var request = repository.findById(requestId)
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

                boolean isEmployee = hasRole(authentication, "ROLE_EMPLOYEE");
                boolean hasPrivilegedRole = hasRole(authentication, "ROLE_MANAGER")
                                || hasRole(authentication, "ROLE_HR")
                                || hasRole(authentication, "ROLE_ADMIN");

                if (isEmployee && !hasPrivilegedRole
                                && !request.getEmployeeId().equals(authentication.getName())) {
                        throw new ForbiddenOperationException("You are not allowed to view another employee history.");
                }
        }

        private boolean hasRole(Authentication authentication, String role) {
                return authentication.getAuthorities().stream()
                                .anyMatch(authority -> role.equals(authority.getAuthority()));
        }
}
