package tn.pi.remoteflowapplication.application.dto;

public record TeleworkQuotaResponse(
        int maxDaysPerWeek,
        long usedDaysThisWeek,
        long remainingDays) {
}
