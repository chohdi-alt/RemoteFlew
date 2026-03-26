package tn.pi.remoteflowapplication.application.service.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.AdminDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.EmployeeDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.HrDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.ManagerDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.MonthlyCountDTO;
import tn.pi.remoteflowapplication.application.dto.TeleworkStatusDTO;
import tn.pi.remoteflowapplication.application.port.out.TeamRepository;
import tn.pi.remoteflowapplication.application.port.out.UserRepository;
import tn.pi.remoteflowapplication.application.service.WorkflowTaskService;
import tn.pi.remoteflowapplication.application.service.DashboardService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTeleworkJpaRepository;
import tn.pi.remoteflowapplication.infrastructure.persistence.projection.MonthlyCountProjection;
import tn.pi.remoteflowapplication.infrastructure.persistence.projection.StatusCountProjection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final SpringTeleworkJpaRepository teleworkRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final WorkflowTaskService workflowTaskService;

    public DashboardServiceImpl(
            SpringTeleworkJpaRepository teleworkRepository,
            UserRepository userRepository,
            TeamRepository teamRepository,
            WorkflowTaskService workflowTaskService) {
        this.teleworkRepository = teleworkRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.workflowTaskService = workflowTaskService;
    }

    @Override
    public AdminDashboardDTO getAdminDashboard(LocalDate from, LocalDate to) {
        Instant fromInstant = toFromInstant(from);
        Instant toExclusiveInstant = toExclusiveToInstant(to);

        Map<RequestStatus, Long> totalsByStatus = toStatusMap(
                teleworkRepository.countByStatusAndDateRange(fromInstant, toExclusiveInstant));
        long total = totalsByStatus.values().stream().mapToLong(Long::longValue).sum();

        return new AdminDashboardDTO(
                totalsByStatus,
                calculateRate(totalsByStatus.get(RequestStatus.APPROVED), total),
                calculateRate(totalsByStatus.get(RequestStatus.REJECTED), total),
                toMonthlyCounts(teleworkRepository.countGroupedByMonth(fromInstant, toExclusiveInstant)),
                workflowTaskService.countPendingTasksForManager(),
                workflowTaskService.countPendingTasksForHr(),
                safeDouble(teleworkRepository.averageTotalCycleTime()));
    }

    @Override
    public HrDashboardDTO getHrDashboard(LocalDate from, LocalDate to) {
        Instant fromInstant = toFromInstant(from);
        Instant toExclusiveInstant = toExclusiveToInstant(to);

        return new HrDashboardDTO(
                toStatusMap(teleworkRepository.countByStatusAndDateRange(fromInstant, toExclusiveInstant)),
                toMonthlyCounts(teleworkRepository.countGroupedByMonth(fromInstant, toExclusiveInstant)),
                workflowTaskService.countPendingTasksForHr(),
                safeDouble(teleworkRepository.averageHrDecisionTime()));
    }

    @Override
    public ManagerDashboardDTO getManagerDashboard(String managerUsername, LocalDate from, LocalDate to) {
        Instant fromInstant = toFromInstant(from);
        Instant toExclusiveInstant = toExclusiveToInstant(to);

        List<Long> managedTeamIds = teamRepository.findManagedTeamIdsByUsername(managerUsername);
        Long teamId = managedTeamIds.isEmpty() ? null : managedTeamIds.get(0);

        if (teamId == null) {
            return new ManagerDashboardDTO(
                    emptyStatusMap(),
                    List.of(),
                    0,
                    0.0);
        }

        Map<RequestStatus, Long> teamTotals = toStatusMap(
                teleworkRepository.countByStatusAndDateRangeForTeam(teamId, fromInstant, toExclusiveInstant));

        long pendingForTeam = teamTotals.getOrDefault(RequestStatus.SUBMITTED, 0L)
                + teamTotals.getOrDefault(RequestStatus.SPECIAL, 0L);

        return new ManagerDashboardDTO(
                teamTotals,
                toMonthlyCounts(teleworkRepository.countGroupedByMonthForTeam(teamId, fromInstant, toExclusiveInstant)),
                pendingForTeam,
                safeDouble(teleworkRepository.averageManagerDecisionTimeForTeam(teamId)));
    }

    @Override
    public EmployeeDashboardDTO getEmployeeDashboard(String employeeUsername, LocalDate from, LocalDate to) {
        Instant fromInstant = toFromInstant(from);
        Instant toExclusiveInstant = toExclusiveToInstant(to);

        List<TeleworkStatusDTO> recentRequests = teleworkRepository
                .findByEmployeeIdOrderBySubmittedAtDesc(employeeUsername, PageRequest.of(0, 10))
                .stream()
                .map(this::toTeleworkStatusDto)
                .toList();

        return new EmployeeDashboardDTO(
                toStatusMap(teleworkRepository.countByStatusAndDateRangeForEmployee(
                        employeeUsername,
                        fromInstant,
                        toExclusiveInstant)),
                toMonthlyCounts(teleworkRepository.countGroupedByMonthForEmployee(
                        employeeUsername,
                        fromInstant,
                        toExclusiveInstant)),
                recentRequests);
    }

    private TeleworkStatusDTO toTeleworkStatusDto(TeleworkRequest request) {
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

    private Map<RequestStatus, Long> toStatusMap(List<StatusCountProjection> projections) {
        EnumMap<RequestStatus, Long> result = emptyStatusMap();
        for (StatusCountProjection projection : projections) {
            result.put(projection.getStatus(), projection.getCount());
        }
        return result;
    }

    private EnumMap<RequestStatus, Long> emptyStatusMap() {
        EnumMap<RequestStatus, Long> result = new EnumMap<>(RequestStatus.class);
        for (RequestStatus status : RequestStatus.values()) {
            result.put(status, 0L);
        }
        return result;
    }

    private List<MonthlyCountDTO> toMonthlyCounts(List<MonthlyCountProjection> projections) {
        return projections.stream()
                .map(projection -> new MonthlyCountDTO(
                        projection.getYear(),
                        projection.getMonth(),
                        projection.getCount()))
                .toList();
    }

    private double calculateRate(Long numerator, long denominator) {
        if (numerator == null || denominator <= 0) {
            return 0.0d;
        }
        return ((double) numerator / (double) denominator) * 100.0d;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0d : value;
    }

    private Instant toFromInstant(LocalDate value) {
        return value == null ? null : value.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant toExclusiveToInstant(LocalDate value) {
        return value == null ? null : value.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
