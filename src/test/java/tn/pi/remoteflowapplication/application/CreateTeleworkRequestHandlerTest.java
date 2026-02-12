package tn.pi.remoteflowapplication.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.pi.remoteflowapplication.application.command.CreateTeleworkRequestHandler;
import tn.pi.remoteflowapplication.application.dto.CreateTeleworkDTO;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.rule.QuotaValidationRule;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Use default config to avoid bypassing real application.properties.
@SpringBootTest
class CreateTeleworkRequestHandlerTest {

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
        private CreateTeleworkRequestHandler handler;

        @Value("${alfresco.telework-folder-id}")
        private String teleworkFolderId;

        @BeforeEach
        void setUp() {
                setAuth("emp-1", "ROLE_EMPLOYEE");
        }

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
        }

        @Test
        void handleValidRequestPersistsAndPublishes() throws Exception {
                CreateTeleworkDTO dto = new CreateTeleworkDTO(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 2),
                                "reason");
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "justif.txt",
                                "text/plain",
                                "x".getBytes());

                when(documentService.upload(any(), eq(teleworkFolderId))).thenReturn("node-1");
                when(camundaWorkflowService.startTeleworkProcess(any(), eq("emp-1"), anyBoolean()))
                                .thenReturn("proc-1");

                handler.handle(dto, file);

                InOrder inOrder = inOrder(quotaRule, repository);
                inOrder.verify(quotaRule).validate(any(), eq(true));
                inOrder.verify(repository, times(1)).save(any());

                verify(repository, times(1)).save(any());
                verify(domainEventPublisher).publishEvents(any());
        }

        @Test
        void handleSpecialCaseStartsWorkflowWithSpecialFlag() throws Exception {
                CreateTeleworkDTO dto = new CreateTeleworkDTO(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 3),
                                "reason");
                MockMultipartFile emptyFile = new MockMultipartFile(
                                "file",
                                "justif.txt",
                                "text/plain",
                                new byte[0]);

                when(quotaRule.isSpecialCase(any())).thenReturn(true);
                when(camundaWorkflowService.startTeleworkProcess(any(), eq("emp-1"), eq(true)))
                                .thenReturn("proc-2");

                handler.handle(dto, emptyFile);

                verify(camundaWorkflowService).startTeleworkProcess(any(), eq("emp-1"), eq(true));
        }

        private void setAuth(String username, String role) {
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                                username,
                                                "n/a",
                                                List.of(new SimpleGrantedAuthority(role))));
        }
}
