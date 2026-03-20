package tn.pi.remoteflowapplication.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tn.pi.remoteflowapplication.application.command.HrApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.application.service.WorkflowTaskService;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;
import tn.pi.remoteflowapplication.domain.entity.TaskEntity;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class HrApprovalHandlerTest {

    private TeleworkRequestRepository repository;
    private WorkflowOrchestrationPort workflowService;
    private WorkflowTaskService workflowTaskService;
    private DomainEventPublisher domainEventPublisher;
    private DocumentStoragePort documentService;
    private HrApprovalHandler handler;

    @BeforeEach
    void setUp() {
        repository = mock(TeleworkRequestRepository.class);
        workflowService = mock(WorkflowOrchestrationPort.class);
        workflowTaskService = mock(WorkflowTaskService.class);
        domainEventPublisher = mock(DomainEventPublisher.class);
        documentService = mock(DocumentStoragePort.class);
        
        handler = new HrApprovalHandler(repository, workflowService, workflowTaskService, domainEventPublisher, documentService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approveSpecialMovesToApproved() {
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            mockedStatic.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            setAuth("hr-1");

            TeleworkRequest request = TeleworkRequest.create(
                    "emp-1",
                    LocalDate.of(2026, 2, 2),
                    LocalDate.of(2026, 2, 3));
            request.markAsSpecial();
            request.linkProcess("proc-1");

            when(repository.findById(1L)).thenReturn(Optional.of(request));

            TaskEntity task = new TaskEntity();
            task.setId(200L);
            task.setJobKey(300L);
            when(workflowTaskService.validateAndGetTask(200L, 1L, "HR")).thenReturn(task);

            handler.approve(1L, "200", new ApprovalDecisionDTO(1L, "hr-1", "ok"));

            assertEquals("APPROVED", request.getStatus().name());
            assertNotNull(request.getHrDecisionAt());
            assertNotNull(request.getApprovedAt());
            assertEquals("hr-1", request.getHrExternalId());
            verify(repository).save(request);
            verify(domainEventPublisher).publishEvents(request);

            // Verify and execute synchronization
            ArgumentCaptor<TransactionSynchronization> syncCaptor = ArgumentCaptor.forClass(TransactionSynchronization.class);
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
            
            TransactionSynchronization sync = syncCaptor.getValue();
            sync.afterCommit();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(workflowService).completeTask(eq("300"), varsCaptor.capture());
            Map<String, Object> vars = varsCaptor.getValue();
            assertEquals("APPROVE", vars.get("decision"));
            assertEquals("ok", vars.get("managerComment"));
            verify(workflowTaskService).completeTask(200L, "hr-1");
        }
    }

    @Test
    void rejectSpecialMovesToRejected() {
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            mockedStatic.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            setAuth("hr-1");

            TeleworkRequest request = TeleworkRequest.create(
                    "emp-1",
                    LocalDate.of(2026, 2, 2),
                    LocalDate.of(2026, 2, 3));
            request.markAsSpecial();
            request.linkProcess("proc-1");

            when(repository.findById(1L)).thenReturn(Optional.of(request));

            TaskEntity task = new TaskEntity();
            task.setId(201L);
            task.setJobKey(301L);
            when(workflowTaskService.validateAndGetTask(201L, 1L, "HR")).thenReturn(task);

            handler.reject(1L, "201", new ApprovalDecisionDTO(1L, "hr-1", "no"));

            assertEquals("REJECTED", request.getStatus().name());
            assertNotNull(request.getHrDecisionAt());
            assertNotNull(request.getRejectedAt());
            assertEquals("hr-1", request.getHrExternalId());
            verify(repository).save(request);
            verify(domainEventPublisher).publishEvents(request);

            // Verify and execute synchronization
            ArgumentCaptor<TransactionSynchronization> syncCaptor = ArgumentCaptor.forClass(TransactionSynchronization.class);
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
            
            TransactionSynchronization sync = syncCaptor.getValue();
            sync.afterCommit();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(workflowService).completeTask(eq("301"), varsCaptor.capture());
            Map<String, Object> vars = varsCaptor.getValue();
            assertEquals("REJECT", vars.get("decision"));
            assertEquals("no", vars.get("managerComment"));
            verify(workflowTaskService).completeTask(201L, "hr-1");
        }
    }

    private void setAuth(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_HR"))));
    }
}
