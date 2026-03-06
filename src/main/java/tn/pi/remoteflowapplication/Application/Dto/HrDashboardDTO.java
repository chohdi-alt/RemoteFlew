package tn.pi.remoteflowapplication.application.dto;

import tn.pi.remoteflowapplication.domain.state.RequestStatus;

import java.util.List;
import java.util.Map;

public record HrDashboardDTO(
        Map<RequestStatus, Long> totalsByStatus,
        List<MonthlyCountDTO> monthlyTrend,
        long pendingHrTasks,
        double avgHrDecisionTime
) {
}
