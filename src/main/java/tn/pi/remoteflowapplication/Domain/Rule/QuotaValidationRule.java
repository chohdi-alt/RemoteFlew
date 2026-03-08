package tn.pi.remoteflowapplication.domain.rule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.service.SystemConfigurationService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;

@Component
public class QuotaValidationRule {

    private static final WeekFields WEEK_FIELDS = WeekFields.ISO;

    private final TeleworkRequestRepository repository;
    private final SystemConfigurationService systemConfigurationService;

    @Value("${telework.max-days-per-week:1}")
    private int maxDaysPerWeek;

    public QuotaValidationRule(
            TeleworkRequestRepository repository,
            SystemConfigurationService systemConfigurationService) {
        this.repository = repository;
        this.systemConfigurationService = systemConfigurationService;
    }

    public void validate(TeleworkRequest request, boolean hasJustificatif) {
        if (request.getStartDate() != null
                && request.getEndDate() != null
                && request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException("La date de debut doit etre avant la date de fin");
        }

        if (exceedsWeeklyQuota(request) && !hasJustificatif) {
            throw new BusinessException("Plus d'un jour de teletravail par semaine necessite un justificatif");
        }
    }

    public boolean isSpecialCase(TeleworkRequest request) {
        return exceedsWeeklyQuota(request);
    }

    private boolean exceedsWeeklyQuota(TeleworkRequest request) {
        if (request.getEmployeeId() == null) {
            return false;
        }

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        if (startDate == null || endDate == null) {
            return false;
        }

        LocalDate weekCursor = startOfWeek(startDate);
        while (!weekCursor.isAfter(endDate)) {
            LocalDate weekStart = weekCursor;
            LocalDate weekEnd = weekCursor.plusDays(6);

            long totalDaysForWeek = totalDaysInWeekIncludingRequest(request, weekStart, weekEnd);
            if (totalDaysForWeek > resolveMaxDaysPerWeek()) {
                return true;
            }

            weekCursor = weekCursor.plusWeeks(1);
        }

        return false;
    }

    private long totalDaysInWeekIncludingRequest(TeleworkRequest request, LocalDate weekStart, LocalDate weekEnd) {
        long existingDays = repository.findByEmployeeIdAndWeek(request.getEmployeeId(), weekStart, weekEnd)
                .stream()
                .filter(r -> request.getId() == null || !request.getId().equals(r.getId()))
                .filter(r -> r.getStatus() != RequestStatus.REJECTED)
                .mapToLong(r -> overlapDays(r.getStartDate(), r.getEndDate(), weekStart, weekEnd))
                .sum();

        long requestedDays = overlapDays(request.getStartDate(), request.getEndDate(), weekStart, weekEnd);
        return existingDays + requestedDays;
    }

    private LocalDate startOfWeek(LocalDate date) {
        return date.with(WEEK_FIELDS.dayOfWeek(), 1);
    }

    private long overlapDays(LocalDate start, LocalDate end, LocalDate rangeStart, LocalDate rangeEnd) {
        if (start == null || end == null || rangeStart == null || rangeEnd == null) {
            return 0;
        }

        LocalDate overlapStart = start.isAfter(rangeStart) ? start : rangeStart;
        LocalDate overlapEnd = end.isBefore(rangeEnd) ? end : rangeEnd;

        if (overlapStart.isAfter(overlapEnd)) {
            return 0;
        }

        return calculateDays(overlapStart, overlapEnd);
    }

    private long calculateDays(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    public int resolveMaxDaysPerWeek() {
        int configured = systemConfigurationService.getTeleworkMaxDaysPerWeek();
        if (configured > 0) {
            return configured;
        }
        return maxDaysPerWeek > 0 ? maxDaysPerWeek : 1;
    }
}
