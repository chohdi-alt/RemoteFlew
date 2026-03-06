package tn.pi.remoteflowapplication.application.dto;

public record MonthlyCountDTO(
        int year,
        int month,
        long count
) {
}
