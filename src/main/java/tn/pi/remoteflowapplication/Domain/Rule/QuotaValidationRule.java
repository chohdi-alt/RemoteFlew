package tn.pi.remoteflowapplication.domain.rule;

import org.springframework.stereotype.Component;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;

@Component
public class QuotaValidationRule {

    private static final int MAX_DAYS_PER_WEEK = 1;
    private static final WeekFields WEEK_FIELDS = WeekFields.ISO;

    private final TeleworkRequestRepository repository;

    public QuotaValidationRule(TeleworkRequestRepository repository) {
        this.repository = repository;
    }

    // Validation mÃ©tier (bloquante)
    public void validate(TeleworkRequest request, boolean hasJustificatif) {

        // Validation des dates
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException(
                    "La date de dÃ©but doit Ãªtre avant la date de fin");
        }

        // RÃ¨gle justificatif obligatoire (sur l'ensemble de la semaine)
        if (exceedsWeeklyQuota(request) && !hasJustificatif) {
            throw new BusinessException(
                    "Plus dâ€™un jour de tÃ©lÃ©travail par semaine nÃ©cessite un justificatif");
        }
    }

    // DÃ©tection du cas spÃ©cial (non bloquante)
    public boolean isSpecialCase(TeleworkRequest request) {
        return exceedsWeeklyQuota(request);
    }

    // MÃ©thode utilitaire interne
    private boolean exceedsWeeklyQuota(TeleworkRequest request) {
        if (request.getEmployeeId() == null) {
            return false;
        }

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        LocalDate weekCursor = startOfWeek(startDate);

        while (!weekCursor.isAfter(endDate)) {
            LocalDate weekStart = weekCursor;
            LocalDate weekEnd = weekCursor.plusDays(6);

            long totalDaysForWeek = totalDaysInWeekIncludingRequest(
                    request,
                    weekStart,
                    weekEnd);

            if (totalDaysForWeek > MAX_DAYS_PER_WEEK) {
                return true;
            }

            weekCursor = weekCursor.plusWeeks(1);
        }

        return false;
    }

    private long totalDaysInWeekIncludingRequest(
            TeleworkRequest request,
            LocalDate weekStart,
            LocalDate weekEnd) {

        long existingDays = repository.findByEmployeeIdAndWeek(
                request.getEmployeeId(),
                weekStart,
                weekEnd)
                .stream()
                .filter(r -> request.getId() == null || !request.getId().equals(r.getId()))
                .filter(r -> r.getStatus() != RequestStatus.REJECTED)
                .mapToLong(r -> overlapDays(
                        r.getStartDate(),
                        r.getEndDate(),
                        weekStart,
                        weekEnd))
                .sum();

        long requestedDays = overlapDays(
                request.getStartDate(),
                request.getEndDate(),
                weekStart,
                weekEnd);

        return existingDays + requestedDays;
    }

    private LocalDate startOfWeek(LocalDate date) {
        return date.with(WEEK_FIELDS.dayOfWeek(), 1);
    }

    private long overlapDays(
            LocalDate start,
            LocalDate end,
            LocalDate rangeStart,
            LocalDate rangeEnd) {

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
}
