package tn.pi.remoteflowapplication.application.dto;

import tn.pi.remoteflowapplication.domain.state.RequestStatus;

import java.util.List;
import java.util.Map;

public record EmployeeDashboardDTO(
        Map<RequestStatus, Long> myTotalsByStatus,
        List<MonthlyCountDTO> myMonthlyTrend,
        List<TeleworkStatusDTO> recentRequests
) {
}
