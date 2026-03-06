package tn.pi.remoteflowapplication.application.service;

import tn.pi.remoteflowapplication.application.dto.AdminDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.EmployeeDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.HrDashboardDTO;
import tn.pi.remoteflowapplication.application.dto.ManagerDashboardDTO;

import java.time.LocalDate;

public interface DashboardService {
    AdminDashboardDTO getAdminDashboard(LocalDate from, LocalDate to);

    HrDashboardDTO getHrDashboard(LocalDate from, LocalDate to);

    ManagerDashboardDTO getManagerDashboard(String managerExternalId, LocalDate from, LocalDate to);

    EmployeeDashboardDTO getEmployeeDashboard(String employeeExternalId, LocalDate from, LocalDate to);
}
