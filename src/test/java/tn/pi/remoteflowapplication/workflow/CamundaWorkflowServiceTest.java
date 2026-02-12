package tn.pi.remoteflowapplication.workflow;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.AssignUserTaskCommandStep1;
import io.camunda.zeebe.client.api.command.CompleteUserTaskCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.zeebe.client.api.response.AssignUserTaskResponse;
import io.camunda.zeebe.client.api.response.CompleteUserTaskResponse;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;

import java.util.concurrent.TimeUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CamundaWorkflowServiceTest {

    @Mock
    private ZeebeClient zeebeClient;
    @Mock
    private CreateProcessInstanceCommandStep1 createStep1;
    @Mock
    private CreateProcessInstanceCommandStep2 createStep2;
    @Mock
    private CreateProcessInstanceCommandStep3 createStep3;
    @Mock
    private ZeebeFuture<ProcessInstanceEvent> processFuture;
    @Mock
    private ProcessInstanceEvent processEvent;

    @Mock
    private AssignUserTaskCommandStep1 assignStep;
    @Mock
    private CompleteUserTaskCommandStep1 completeStep;
    @Mock
    private ZeebeFuture<AssignUserTaskResponse> assignFuture;
    @Mock
    private ZeebeFuture<CompleteUserTaskResponse> completeFuture;
    @Mock
    private AssignUserTaskResponse assignResponse;
    @Mock
    private CompleteUserTaskResponse completeResponse;

    private CamundaWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new CamundaWorkflowService(zeebeClient);
    }

    @Test
    void startTeleworkProcessSendsCreateInstanceCommand() throws Exception {
        when(zeebeClient.newCreateInstanceCommand()).thenReturn(createStep1);
        when(createStep1.bpmnProcessId("telework_process")).thenReturn(createStep2);
        when(createStep2.latestVersion()).thenReturn(createStep3);
        when(createStep3.variables(anyMap())).thenReturn(createStep3);
        when(createStep3.send()).thenReturn(processFuture);
        when(processFuture.get(10, TimeUnit.SECONDS)).thenReturn(processEvent);
        when(processEvent.getProcessInstanceKey()).thenReturn(123L);

        String id = service.startTeleworkProcess(1L, "emp-1", false);

        assertEquals("123", id);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(createStep3).variables(varsCaptor.capture());

        Map<String, Object> vars = varsCaptor.getValue();
        assertEquals(1L, vars.get("requestId"));
        assertEquals("emp-1", vars.get("employeeId"));
        assertEquals(false, vars.get("specialCase"));
    }

    @Test
    void completeTaskAssignsAndCompletes() throws Exception {
        long taskKey = 100L;

        when(zeebeClient.newUserTaskAssignCommand(taskKey)).thenReturn(assignStep);
        when(assignStep.assignee("manager-1")).thenReturn(assignStep);
        when(assignStep.allowOverride(false)).thenReturn(assignStep);
        when(assignStep.send()).thenReturn(assignFuture);
        when(assignFuture.get(5, TimeUnit.SECONDS)).thenReturn(assignResponse);

        when(zeebeClient.newUserTaskCompleteCommand(taskKey)).thenReturn(completeStep);
        when(completeStep.variables(anyMap())).thenReturn(completeStep);
        when(completeStep.send()).thenReturn(completeFuture);
        when(completeFuture.get(5, TimeUnit.SECONDS)).thenReturn(completeResponse);

        service.completeTask("100", "APPROVE", "ok", "manager-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(completeStep).variables(varsCaptor.capture());

        Map<String, Object> vars = varsCaptor.getValue();
        assertEquals("APPROVE", vars.get("decision"));
        assertEquals("ok", vars.get("comment"));

        verify(zeebeClient).newUserTaskAssignCommand(taskKey);
        verify(zeebeClient).newUserTaskCompleteCommand(taskKey);
    }

    @Test
    void validateTaskKeyThrowsOnInvalidFormat() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateTaskKeyForRequest("abc", "proc-1", 1L, "ROLE_MANAGER"));
    }

    @Test
    void validateTaskKeyThrowsOnMissingContext() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateTaskKeyForRequest("100", null, 1L, "ROLE_MANAGER"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateTaskKeyForRequest("100", "proc-1", null, "ROLE_MANAGER"));
    }
}
