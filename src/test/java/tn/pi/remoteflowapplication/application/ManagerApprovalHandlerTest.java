package tn.pi.remoteflowapplication.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.rule.QuotaValidationRule;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class ManagerApprovalHandlerTest {

        @MockBean
        private TeleworkRequestRepository repository;
        @MockBean
        private CamundaWorkflowService camundaWorkflowService;
        @MockBean
        private QuotaValidationRule quotaValidationRule;
        @MockBean
        private DomainEventPublisher domainEventPublisher;
        @MockBean
        private AlfrescoDocumentService documentService;

        @Autowired
        private ManagerApprovalHandler handler;

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
        }

        @Test
        void approveMarksApprovedWhenNotSpecial() {
                setAuth("manager-1");

                TeleworkRequest request = TeleworkRequest.create(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 2));
                request.linkProcess("proc-1");

                when(repository.findById(1L)).thenReturn(Optional.of(request));
                when(quotaValidationRule.isSpecialCase(any())).thenReturn(false);

                handler.approve(1L, "100", new ApprovalDecisionDTO(1L, "manager-1", "ok"));

                assertEquals("APPROVED", request.getStatus().name());
                verify(repository).save(request);
                verify(domainEventPublisher).publishEvents(request);
        }

        @Test
        void rejectMovesToRejected() {
                setAuth("manager-1");

                TeleworkRequest request = TeleworkRequest.create(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 2));
                request.linkProcess("proc-1");

                when(repository.findById(1L)).thenReturn(Optional.of(request));

                handler.reject(1L, "101", new ApprovalDecisionDTO(1L, "manager-1", "no"));

                assertEquals("REJECTED", request.getStatus().name());
                verify(repository).save(request);
                verify(domainEventPublisher).publishEvents(request);
        }

        @Test
        void approvalValidatesTaskKeyCorrelation() {
                setAuth("manager-1");

                TeleworkRequest request = TeleworkRequest.create(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 2));
                request.linkProcess("proc-1");

                when(repository.findById(1L)).thenReturn(Optional.of(request));
                when(quotaValidationRule.isSpecialCase(any())).thenReturn(false);

                handler.approve(1L, "9999", new ApprovalDecisionDTO(1L, "manager-1", "ok"));

                verify(camundaWorkflowService)
                                .validateTaskKeyForRequest("9999", "proc-1", 1L, "ROLE_MANAGER");
        }

        private void setAuth(String username) {
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                                username,
                                                "n/a",
                                                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
        }
}
