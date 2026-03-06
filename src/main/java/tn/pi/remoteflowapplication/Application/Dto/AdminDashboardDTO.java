package tn.pi.remoteflowapplication.application.dto;

import tn.pi.remoteflowapplication.domain.state.RequestStatus;

import java.util.List;
import java.util.Map;

public record AdminDashboardDTO(
        Map<RequestStatus, Long> totalsByStatus,
        double approvalRate,
        double rejectionRate,
        List<MonthlyCountDTO> monthlyTrend,
        long pendingManagerTasks,
        long pendingHrTasks,
        double avgCycleTime
) {
}
