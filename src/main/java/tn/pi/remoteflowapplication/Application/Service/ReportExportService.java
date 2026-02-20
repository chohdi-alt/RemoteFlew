package tn.pi.remoteflowapplication.application.service;

import tn.pi.remoteflowapplication.application.dto.ExportFormat;
import tn.pi.remoteflowapplication.application.dto.ExportedReportDTO;
import tn.pi.remoteflowapplication.application.dto.ReportRequestDTO;

public interface ReportExportService {
    ExportedReportDTO export(ReportRequestDTO request, ExportFormat format);
}

