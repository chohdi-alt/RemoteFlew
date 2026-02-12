package tn.pi.remoteflowapplication.application.query;

import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.AuditHistoryDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkStatusDTO;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.util.List;
import java.util.stream.Collectors;

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

        public List<AuditHistoryDTO> getRequestHistory(Long requestId) {
                var request = repository.findById(requestId)
                                .orElseThrow(() -> new RuntimeException("Request not found"));
                return request.consulterHistorique().stream()
                                .map(log -> new AuditHistoryDTO(
                                                log.getAction(),
                                                log.getEntite(),
                                                log.getTimestamp(),
                                                log.getUtilisateur()))
                                .collect(Collectors.toList());
        }
}
