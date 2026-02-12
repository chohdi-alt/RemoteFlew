package tn.pi.remoteflowapplication.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.command.HrApprovalHandler;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.rule.QuotaValidationRule;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Use default config to avoid bypassing real application.properties.
@SpringBootTest
class WorkflowInvocationTest {

        @MockBean
        private TeleworkRequestRepository repository;
        @MockBean
        private AlfrescoDocumentService documentService;
        @MockBean
        private QuotaValidationRule quotaRule;
        @MockBean
        private CamundaWorkflowService camundaWorkflowService;
        @MockBean
        private DomainEventPublisher domainEventPublisher;

        @Autowired
        private CreateTeleworkRequestHandler createHandler;

        @Autowired
        private ManagerApprovalHandler managerApprovalHandler;

        @Autowired
        private HrApprovalHandler hrApprovalHandler;

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
        }

        @Test
        void createInvokesStartProcess() throws Exception {
                setAuth("emp-1", "ROLE_EMPLOYEE");

                CreateTeleworkDTO dto = new CreateTeleworkDTO(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 2),
                                "reason");

                MockMultipartFile emptyFile = new MockMultipartFile(
                                "file",
                                "justif.txt",
                                "text/plain",
                                new byte[0]);

                when(camundaWorkflowService.startTeleworkProcess(any(), eq("emp-1"), anyBoolean()))
                                .thenReturn("proc-1");

                createHandler.handle(dto, emptyFile);

                verify(camundaWorkflowService).startTeleworkProcess(any(), eq("emp-1"), eq(false));
        }

        @Test
        void managerApproveInvokesCompleteTask() {
                setAuth("manager-1", "ROLE_MANAGER");

                TeleworkRequest request = TeleworkRequest.create(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 2));
                request.linkProcess("proc-1");

                when(repository.findById(1L)).thenReturn(Optional.of(request));
                when(quotaRule.isSpecialCase(any())).thenReturn(false);

                managerApprovalHandler.approve(1L, "100", new ApprovalDecisionDTO(1L, "manager-1", "ok"));

                verify(camundaWorkflowService).completeTask("100", "APPROVE", "ok", "manager-1");
        }

        @Test
        void hrApproveInvokesCompleteTask() {
                setAuth("hr-1", "ROLE_HR");

                TeleworkRequest request = TeleworkRequest.create(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 3));
                request.markAsSpecial();
                request.linkProcess("proc-1");

                when(repository.findById(1L)).thenReturn(Optional.of(request));

                hrApprovalHandler.approve(1L, "200", new ApprovalDecisionDTO(1L, "hr-1", "ok"));

                verify(camundaWorkflowService).completeTask("200", "APPROVE", "ok", "hr-1");
        }

        private void setAuth(String username, String role) {
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                                username,
                                                "n/a",
                                                List.of(new SimpleGrantedAuthority(role))));
        }
}
