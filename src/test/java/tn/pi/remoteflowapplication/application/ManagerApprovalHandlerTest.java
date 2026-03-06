package tn.pi.remoteflowapplication.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.rule.QuotaValidationRule;
import tn.pi.remoteflowapplication.domain.state.RequestStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ManagerApprovalHandlerTest {

    private TeleworkRequestRepository repository;
    private WorkflowOrchestrationPort workflowService;
    private QuotaValidationRule quotaValidationRule;
    private DomainEventPublisher domainEventPublisher;
    private DocumentStoragePort documentService;
    private ManagerApprovalHandler handler;

    @BeforeEach
    void setUp() {
        repository = mock(TeleworkRequestRepository.class);
        workflowService = mock(WorkflowOrchestrationPort.class);
        quotaValidationRule = mock(QuotaValidationRule.class);
        domainEventPublisher = mock(DomainEventPublisher.class);
        documentService = mock(DocumentStoragePort.class);
        handler = new ManagerApprovalHandler(repository, workflowService, quotaValidationRule, domainEventPublisher,
                documentService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager-1", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approve_Successful() {
        TeleworkRequest request = TeleworkRequest.create("emp-1", LocalDate.now(), LocalDate.now().plusDays(1));
        request.linkProcess("proc-1");

        when(repository.findById(1L)).thenReturn(Optional.of(request));
        when(quotaValidationRule.isSpecialCase(request)).thenReturn(false);

        handler.approve(1L, "task-1", new ApprovalDecisionDTO(1L, "manager-1", "Approved"));

        verify(repository).save(request);
        verify(domainEventPublisher).publishEvents(request);
        // Note: afterCommit logic requires TransactionSynchronizationManager mock or
        // integration test
    }

    @Test
    void reject_Successful() {
        TeleworkRequest request = TeleworkRequest.create("emp-1", LocalDate.now(), LocalDate.now().plusDays(1));
        request.linkProcess("proc-1");

        when(repository.findById(1L)).thenReturn(Optional.of(request));

        handler.reject(1L, "task-1", new ApprovalDecisionDTO(1L, "manager-1", "Rejected"));

        verify(repository).save(request);
        verify(domainEventPublisher).publishEvents(request);
    }
}
