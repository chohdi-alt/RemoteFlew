package tn.pi.remoteflowapplication.infrastructure.workflow;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;
import tn.pi.remoteflowapplication.domain.exception.WorkflowExecutionException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CamundaWorkflowService implements WorkflowOrchestrationPort {

    private static final Logger logger = LoggerFactory.getLogger(CamundaWorkflowService.class);
    private static final String TELEWORK_PROCESS_ID = "telework_process";

    private final ZeebeClient zeebeClient;

    public CamundaWorkflowService(ZeebeClient zeebeClient) {
        this.zeebeClient = zeebeClient;
    }

    @Override
    public String startTeleworkProcess(Long requestId, String employeeId, Boolean specialCase) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", requestId);
        variables.put("employeeId", employeeId);
        variables.put("specialCase", specialCase);

        try {
            ProcessInstanceEvent event = zeebeClient.newCreateInstanceCommand()
                    .bpmnProcessId(TELEWORK_PROCESS_ID)
                    .latestVersion()
                    .variables(variables)
                    .send()
                    .get(10, TimeUnit.SECONDS);
            return String.valueOf(event.getProcessInstanceKey());
        } catch (Exception e) {
            throw new WorkflowExecutionException("Failed to start workflow within timeout", e);
        }
    }

    @Override
    public void completeTask(String taskKey, Map<String, Object> variables) {
        long key = parseTaskKey(taskKey);
        Map<String, Object> payload = variables == null ? new HashMap<>() : new HashMap<>(variables);

        try {
            zeebeClient.newCompleteCommand(key)
                    .variables(payload)
                    .send()
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new WorkflowExecutionException("Failed to complete task " + taskKey, e);
        }
    }

    @Override
    public void completeTask(String taskKey, String decision, String comment, String assignee) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("decision", decision);
        if (comment != null) {
            variables.put("managerComment", comment);
        }
        completeTask(taskKey, variables);
    }

    private long parseTaskKey(String taskKey) {
        try {
            long key = Long.parseLong(taskKey);
            if (key <= 0) {
                throw new IllegalArgumentException("Task key must be positive");
            }
            return key;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid task key format", ex);
        }
    }
}
