package tn.pi.remoteflowapplication.application.query;

import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.ReportRequestDTO;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportQueryService {

    private final TeleworkRequestRepository repository;

    public ReportQueryService(TeleworkRequestRepository repository) {
        this.repository = repository;
    }

    /**
     * Generates a comprehensive report based on the provided filters.
     * Calculates statistics for telework requests, including distribution by status
     * and department/team.
     */
    public Object generateReport(ReportRequestDTO dto) {
        List<TeleworkRequest> allRequests = repository.findAll();

        // Filter requests based on DTO parameters
        List<TeleworkRequest> filtered = allRequests.stream()
                .filter(r -> dto.getFromDate() == null || !r.getStartDate().isBefore(dto.getFromDate()))
                .filter(r -> dto.getToDate() == null || !r.getEndDate().isAfter(dto.getToDate()))
                .filter(r -> dto.getStatus() == null || r.getStatus().name().equalsIgnoreCase(dto.getStatus()))
                .filter(r -> dto.getDepartment() == null
                        || (r.getEquipe() != null && r.getEquipe().getNom().equalsIgnoreCase(dto.getDepartment())))
                .collect(Collectors.toList());

        Map<String, Object> report = new HashMap<>();
        report.put("summary", summarize(filtered));
        report.put("filters", dto);

        // If no specific department is filtered, provide a breakdown by department
        if (dto.getDepartment() == null) {
            report.put("byDepartment", groupByDepartment(filtered));
        }

        return report;
    }

    private Map<String, Object> summarize(List<TeleworkRequest> requests) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalRequests", requests.size());
        summary.put("approvedCount", countByStatus(requests, RequestStatus.APPROVED));
        summary.put("rejectedCount", countByStatus(requests, RequestStatus.REJECTED));
        summary.put("pendingCount", countByStatus(requests, RequestStatus.SUBMITTED));
        summary.put("specialCasesCount", countByStatus(requests, RequestStatus.SPECIAL));

        double approvalRate = requests.isEmpty() ? 0.0
                : (double) countByStatus(requests, RequestStatus.APPROVED) / requests.size();
        summary.put("approvalRate", Math.round(approvalRate * 100.0) / 100.0);

        return summary;
    }

    private Map<String, Map<String, Object>> groupByDepartment(List<TeleworkRequest> requests) {
        return requests.stream()
                .filter(r -> r.getEquipe() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getEquipe().getNom(),
                        Collectors.collectingAndThen(Collectors.toList(), this::summarize)));
    }

    private long countByStatus(List<TeleworkRequest> requests, RequestStatus status) {
        return requests.stream()
                .filter(r -> r.getStatus() == status)
                .count();
    }
}
