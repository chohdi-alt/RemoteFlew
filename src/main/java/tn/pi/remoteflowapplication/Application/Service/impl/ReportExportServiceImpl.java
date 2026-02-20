package tn.pi.remoteflowapplication.application.service.impl;

import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.dto.ExportFormat;
import tn.pi.remoteflowapplication.application.dto.ExportedReportDTO;
import tn.pi.remoteflowapplication.application.dto.ReportRequestDTO;
import tn.pi.remoteflowapplication.application.query.ReportQueryService;
import tn.pi.remoteflowapplication.application.service.ReportExportService;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ReportExportServiceImpl implements ReportExportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String BASE_NAME = "telework-report-";

    private final ReportQueryService reportQueryService;

    public ReportExportServiceImpl(ReportQueryService reportQueryService) {
        this.reportQueryService = reportQueryService;
    }

    @Override
    public ExportedReportDTO export(ReportRequestDTO request, ExportFormat format) {
        Object report = reportQueryService.generateReport(request);
        if (!(report instanceof Map<?, ?> reportMapRaw)) {
            throw new BusinessException("Unable to export report: unsupported report payload");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> reportMap = (Map<String, Object>) reportMapRaw;
        String timestamp = LocalDateTime.now().format(FILE_TS);

        return switch (format) {
            case CSV -> new ExportedReportDTO(
                    BASE_NAME + timestamp + ".csv",
                    "text/csv",
                    buildCsv(reportMap).getBytes(StandardCharsets.UTF_8));
            case PDF -> new ExportedReportDTO(
                    BASE_NAME + timestamp + ".pdf",
                    "application/pdf",
                    buildPdfLikeText(reportMap).getBytes(StandardCharsets.UTF_8));
            case XLSX -> new ExportedReportDTO(
                    BASE_NAME + timestamp + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    buildCsv(reportMap).getBytes(StandardCharsets.UTF_8));
        };
    }

    private String buildCsv(Map<String, Object> reportMap) {
        StringBuilder csv = new StringBuilder();
        csv.append("key,value\n");

        Object summary = reportMap.get("summary");
        if (summary instanceof Map<?, ?> summaryMapRaw) {
            Map<String, Object> summaryMap = new TreeMap<>();
            for (Map.Entry<?, ?> e : summaryMapRaw.entrySet()) {
                summaryMap.put(String.valueOf(e.getKey()), e.getValue());
            }
            for (Map.Entry<String, Object> e : summaryMap.entrySet()) {
                csv.append(escapeCsv(e.getKey()))
                        .append(",")
                        .append(escapeCsv(String.valueOf(e.getValue())))
                        .append("\n");
            }
        } else {
            csv.append("report,").append(escapeCsv(String.valueOf(reportMap))).append("\n");
        }

        return csv.toString();
    }

    private String buildPdfLikeText(Map<String, Object> reportMap) {
        StringBuilder text = new StringBuilder();
        text.append("RemoteFlow Report Export\n\n");
        text.append("Summary:\n");
        Object summary = reportMap.get("summary");
        if (summary instanceof Map<?, ?> summaryMapRaw) {
            Map<String, Object> summaryMap = new TreeMap<>();
            for (Map.Entry<?, ?> e : summaryMapRaw.entrySet()) {
                summaryMap.put(String.valueOf(e.getKey()), e.getValue());
            }
            for (Map.Entry<String, Object> e : summaryMap.entrySet()) {
                text.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        } else {
            text.append(String.valueOf(reportMap)).append("\n");
        }
        return text.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

