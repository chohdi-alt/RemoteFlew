package tn.pi.remoteflowapplication.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.pi.remoteflowapplication.application.dto.ReportRequestDTO;
import tn.pi.remoteflowapplication.application.query.ReportQueryService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {

    @Mock
    private TeleworkRequestRepository repository;

    @InjectMocks
    private ReportQueryService service;

    @Test
    @SuppressWarnings("unchecked")
    void generateReportCalculatesStatisticsCorrectly() {
        TeleworkRequest req1 = TeleworkRequest.create("emp-1", LocalDate.now(), LocalDate.now());
        req1.approveByManager("manager ok");
        req1.approveByHR("ok");

        TeleworkRequest req2 = TeleworkRequest.create("emp-2", LocalDate.now(), LocalDate.now());
        req2.rejectByManager("no");

        when(repository.findAll()).thenReturn(List.of(req1, req2));

        ReportRequestDTO dto = new ReportRequestDTO();
        Map<String, Object> report = (Map<String, Object>) service.generateReport(dto);

        assertNotNull(report.get("summary"));
        Map<String, Object> summary = (Map<String, Object>) report.get("summary");

        assertEquals(2, summary.get("totalRequests"));
        assertEquals(1L, summary.get("approvedCount"));
        assertEquals(1L, summary.get("rejectedCount"));
        assertEquals(0.5, summary.get("approvalRate"));
    }
}
