package tn.pi.remoteflowapplication.workflow;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.zeebe.client.api.response.CompleteJobResponse;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
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
    private CompleteJobCommandStep1 completeStep1;
    @Mock
    private ZeebeFuture<CompleteJobResponse> completeFuture;
    @Mock
    private CompleteJobResponse completeResponse;

    private CamundaWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new CamundaWorkflowService(zeebeClient);
    }

    @Test
    void startTeleworkProcess_Successful() throws Exception {
        when(zeebeClient.newCreateInstanceCommand()).thenReturn(createStep1);
        when(createStep1.bpmnProcessId("telework_process")).thenReturn(createStep2);
        when(createStep2.latestVersion()).thenReturn(createStep3);
        when(createStep3.variables(anyMap())).thenReturn(createStep3);
        when(createStep3.send()).thenReturn(processFuture);
        when(processFuture.get(eq(10L), eq(TimeUnit.SECONDS))).thenReturn(processEvent);
        when(processEvent.getProcessInstanceKey()).thenReturn(123L);

        String result = service.startTeleworkProcess(1L, "emp-1", false);

        assertEquals("123", result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(createStep3).variables(varsCaptor.capture());
        Map<String, Object> vars = varsCaptor.getValue();
        assertEquals(1L, vars.get("requestId"));
        assertEquals("emp-1", vars.get("employeeId"));
    }

    @Test
    void completeTask_Successful() throws Exception {
        long taskKey = 100L;
        when(zeebeClient.newCompleteCommand(taskKey)).thenReturn(completeStep1);
        when(completeStep1.variables(anyMap())).thenReturn(completeStep1);
        when(completeStep1.send()).thenReturn(completeFuture);
        when(completeFuture.get(eq(10L), eq(TimeUnit.SECONDS))).thenReturn(completeResponse);

        service.completeTask("100", "APPROVE", "ok", "manager-1");

        verify(zeebeClient).newCompleteCommand(taskKey);
    }

}
