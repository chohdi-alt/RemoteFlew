package tn.pi.remoteflowapplication.application.service;

import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.TeleworkQuotaResponse;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;

@Service
public class TeleworkQuotaService {

    private static final WeekFields WEEK_FIELDS = WeekFields.ISO;

    private final TeleworkRequestRepository teleworkRequestRepository;
    private final SystemConfigurationService systemConfigurationService;

    public TeleworkQuotaService(
            TeleworkRequestRepository teleworkRequestRepository,
            SystemConfigurationService systemConfigurationService) {
        this.teleworkRequestRepository = teleworkRequestRepository;
        this.systemConfigurationService = systemConfigurationService;
    }

    public TeleworkQuotaResponse getCurrentWeekQuota(String employeeId) {
        return getQuotaForDate(employeeId, LocalDate.now());
    }

    public TeleworkQuotaResponse getQuotaForDate(String employeeId, LocalDate referenceDate) {
        LocalDate date = referenceDate != null ? referenceDate : LocalDate.now();
        LocalDate weekStart = date.with(WEEK_FIELDS.dayOfWeek(), 1);
        LocalDate weekEnd = weekStart.plusDays(6);
        int maxDaysPerWeek = systemConfigurationService.getTeleworkMaxDaysPerWeek();

        long usedDays = teleworkRequestRepository.findByEmployeeIdAndWeek(employeeId, weekStart, weekEnd)
                .stream()
                .filter(request -> request.getStatus() != RequestStatus.REJECTED)
                .mapToLong(request -> overlapDays(request.getStartDate(), request.getEndDate(), weekStart, weekEnd))
                .sum();

        long remaining = Math.max(0L, maxDaysPerWeek - usedDays);
        return new TeleworkQuotaResponse(maxDaysPerWeek, usedDays, remaining);
    }

    private long overlapDays(LocalDate start, LocalDate end, LocalDate rangeStart, LocalDate rangeEnd) {
        if (start == null || end == null) {
            return 0L;
        }

        LocalDate overlapStart = start.isAfter(rangeStart) ? start : rangeStart;
        LocalDate overlapEnd = end.isBefore(rangeEnd) ? end : rangeEnd;
        if (overlapStart.isAfter(overlapEnd)) {
            return 0L;
        }

        return ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
    }
}
