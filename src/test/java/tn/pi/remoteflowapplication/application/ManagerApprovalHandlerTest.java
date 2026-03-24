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
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
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
import static org.mockito.Mockito.*;

class ManagerApprovalHandlerTest {

    private TeleworkRequestRepository repository;
    private WorkflowOrchestrationPort workflowService;
    private DomainEventPublisher domainEventPublisher;
    private DocumentStoragePort documentService;
    private WorkflowTaskService workflowTaskService;
    private ManagerApprovalHandler handler;

    @BeforeEach
    void setUp() {
        repository = mock(TeleworkRequestRepository.class);
        workflowService = mock(WorkflowOrchestrationPort.class);
        domainEventPublisher = mock(DomainEventPublisher.class);
        documentService = mock(DocumentStoragePort.class);
        workflowTaskService = mock(WorkflowTaskService.class);
        handler = new ManagerApprovalHandler(repository, workflowService, workflowTaskService, domainEventPublisher,
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
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            mockedStatic.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            
            TeleworkRequest request = TeleworkRequest.create("emp-1", LocalDate.now(), LocalDate.now().plusDays(1));
            request.linkProcess("proc-1");

            when(repository.findById(1L)).thenReturn(Optional.of(request));
            TaskEntity task = new TaskEntity();
            task.setId(10L);
            task.setJobKey(200L);
            when(workflowTaskService.validateAndGetTask(10L, 1L, "MANAGER")).thenReturn(task);

            handler.approve(1L, "10", new ApprovalDecisionDTO(1L, "manager-1", "Approved"));

            verify(repository).save(request);
            verify(domainEventPublisher).publishEvents(request);

            // Verify and execute synchronization
            ArgumentCaptor<TransactionSynchronization> syncCaptor = ArgumentCaptor.forClass(TransactionSynchronization.class);
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
            
            TransactionSynchronization sync = syncCaptor.getValue();
            sync.afterCommit();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(workflowService).completeTask(eq("200"), varsCaptor.capture());
            Map<String, Object> vars = varsCaptor.getValue();
            assertEquals("APPROVE", vars.get("decision"));
            assertEquals("Approved", vars.get("managerComment"));
            verify(workflowTaskService).completeTask(10L, "manager-1");
        }
    }

    @Test
    void reject_Successful() {
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            mockedStatic.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);

            TeleworkRequest request = TeleworkRequest.create("emp-1", LocalDate.now(), LocalDate.now().plusDays(1));
            request.linkProcess("proc-1");

            when(repository.findById(1L)).thenReturn(Optional.of(request));

            TaskEntity task = new TaskEntity();
            task.setId(11L);
            task.setJobKey(201L);
            when(workflowTaskService.validateAndGetTask(11L, 1L, "MANAGER")).thenReturn(task);

            handler.reject(1L, "11", new ApprovalDecisionDTO(1L, "manager-1", "Rejected"));

            verify(repository).save(request);
            verify(domainEventPublisher).publishEvents(request);

            // Verify and execute synchronization
            ArgumentCaptor<TransactionSynchronization> syncCaptor = ArgumentCaptor.forClass(TransactionSynchronization.class);
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
            
            TransactionSynchronization sync = syncCaptor.getValue();
            sync.afterCommit();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(workflowService).completeTask(eq("201"), varsCaptor.capture());
            Map<String, Object> vars = varsCaptor.getValue();
            assertEquals("REJECT", vars.get("decision"));
            assertEquals("Rejected", vars.get("managerComment"));
            verify(workflowTaskService).completeTask(11L, "manager-1");
        }
    }
}
