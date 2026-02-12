package tn.pi.remoteflowapplication.application.dto;

import java.time.LocalDate;

public class ReportRequestDTO {

    private LocalDate fromDate;
    private LocalDate toDate;
    private String department;
    private String status;

    public ReportRequestDTO() {
    }

    public ReportRequestDTO(
            LocalDate fromDate,
            LocalDate toDate,
            String department,
            String status
    ) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.department = department;
        this.status = status;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public String getDepartment() {
        return department;
    }

    public String getStatus() {
        return status;
    }
}
