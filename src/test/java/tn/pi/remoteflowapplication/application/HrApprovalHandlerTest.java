package tn.pi.remoteflowapplication.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.pi.remoteflowapplication.application.command.HrApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class HrApprovalHandlerTest {

        @MockBean
        private TeleworkRequestRepository repository;
        @MockBean
        private CamundaWorkflowService camundaWorkflowService;
        @MockBean
        private DomainEventPublisher domainEventPublisher;
        @MockBean
        private AlfrescoDocumentService documentService;

        @Autowired
        private HrApprovalHandler handler;

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
        }

        @Test
        void approveSpecialMovesToApproved() {
                setAuth("hr-1");

                TeleworkRequest request = TeleworkRequest.create(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 3));
                request.markAsSpecial();
                request.linkProcess("proc-1");

                when(repository.findById(1L)).thenReturn(Optional.of(request));

                handler.approve(1L, "200", new ApprovalDecisionDTO(1L, "hr-1", "ok"));

                assertEquals("APPROVED", request.getStatus().name());
                assertNotNull(request.getHrDecisionAt());
                assertNotNull(request.getApprovedAt());
                assertEquals("hr-1", request.getHrExternalId());
                verify(repository).save(request);
                verify(domainEventPublisher).publishEvents(request);
        }

        @Test
        void rejectSpecialMovesToRejected() {
                setAuth("hr-1");

                TeleworkRequest request = TeleworkRequest.create(
                                "emp-1",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 3));
                request.markAsSpecial();
                request.linkProcess("proc-1");

                when(repository.findById(1L)).thenReturn(Optional.of(request));

                handler.reject(1L, "201", new ApprovalDecisionDTO(1L, "hr-1", "no"));

                assertEquals("REJECTED", request.getStatus().name());
                assertNotNull(request.getHrDecisionAt());
                assertNotNull(request.getRejectedAt());
                assertEquals("hr-1", request.getHrExternalId());
                verify(repository).save(request);
                verify(domainEventPublisher).publishEvents(request);
        }

        private void setAuth(String username) {
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                                username,
                                                "n/a",
                                                List.of(new SimpleGrantedAuthority("ROLE_HR"))));
        }
}
