package tn.pi.remoteflowapplication.telework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.command.HrApprovalHandler;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;
import tn.pi.remoteflowapplication.application.service.EmailNotificationService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:teleworktest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class TeleworkWorkflowIntegrationTest {

    @Autowired
    private CreateTeleworkRequestHandler createHandler;

    @Autowired
    private ManagerApprovalHandler managerApprovalHandler;

    @Autowired
    private HrApprovalHandler hrApprovalHandler;

    @Autowired
    private TeleworkRequestRepository repository;

    @MockBean
    private CamundaWorkflowService camundaWorkflowService;

    @MockBean
    private AlfrescoDocumentService documentService;

    @MockBean
    private EmailNotificationService emailNotificationService;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        List<TeleworkRequest> requests = repository.findAll();
        for (TeleworkRequest request : requests) {
            repository.deleteById(request.getId());
        }
    }

    @Test
    void submitManagerApproveThenHrApprove() throws Exception {
        authenticateAs("employee-1");

        CreateTeleworkDTO dto = new CreateTeleworkDTO(
                "employee-1",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 3),
                "Need 2 days");

        MultipartFile justificatif = new MockMultipartFile(
                "file",
                "justificatif.txt",
                "text/plain",
                "ok".getBytes());

        when(documentService.upload(any(MultipartFile.class), anyString()))
                .thenReturn("node-1");
        when(camundaWorkflowService.startTeleworkProcess(any(), eq("employee-1"), anyBoolean()))
                .thenReturn("proc-1");

        createHandler.handle(dto, justificatif);

        TeleworkRequest request = repository.findByEmployeeId("employee-1").get(0);
        assertEquals("SPECIAL", request.getStatus().name());

        authenticateAs("hr-1");
        hrApprovalHandler.approve(
                request.getId(),
                "200",
                new ApprovalDecisionDTO(request.getId(), "hr-1", "Approved by HR"));

        TeleworkRequest afterHr = repository.findById(request.getId()).orElseThrow();
        assertEquals("APPROVED", afterHr.getStatus().name());
    }

    @Test
    void submitThenManagerReject() throws Exception {
        authenticateAs("employee-2");

        CreateTeleworkDTO dto = new CreateTeleworkDTO(
                "employee-2",
                LocalDate.of(2026, 2, 5),
                LocalDate.of(2026, 2, 5),
                "One day request");

        when(camundaWorkflowService.startTeleworkProcess(any(), eq("employee-2"), anyBoolean()))
                .thenReturn("proc-2");
        createHandler.handle(dto, null);

        TeleworkRequest request = repository.findByEmployeeId("employee-2").get(0);
        assertEquals("SUBMITTED", request.getStatus().name());

        authenticateAs("manager-2");
        managerApprovalHandler.reject(
                request.getId(),
                "3",
                new ApprovalDecisionDTO(request.getId(), "manager-2", "Rejected by manager"));

        TeleworkRequest afterManager = repository.findById(request.getId()).orElseThrow();
        assertEquals("REJECTED", afterManager.getStatus().name());
    }

    private void authenticateAs(String username) {
        String role;
        if (username.startsWith("manager")) {
            role = "ROLE_MANAGER";
        } else if (username.startsWith("hr")) {
            role = "ROLE_HR";
        } else {
            role = "ROLE_EMPLOYEE";
        }
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, "n/a",
                List.of(() -> role));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
