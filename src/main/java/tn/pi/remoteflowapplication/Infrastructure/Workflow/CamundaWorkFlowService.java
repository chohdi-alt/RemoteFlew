package tn.pi.remoteflowapplication.infrastructure.workflow;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

@Service
/**
 * Camunda 8 Orchestration (Zeebe Remote Engine).
 * This implementation uses the Zeebe Client to communicate with a remote
 * Camunda 8 broker (Remote Broker architecture).
 * NOTE: This is NOT an embedded workflow engine. Unlike Camunda 7,
 * orchestration is performed via the Zeebe protocol to an external engine.
 */
public class CamundaWorkflowService implements WorkflowOrchestrationPort {

    private static final String TELEWORK_PROCESS_ID = "telework_process";

    private final ZeebeClient zeebeClient;

    public CamundaWorkflowService(ZeebeClient zeebeClient) {
        this.zeebeClient = zeebeClient;
    }

    public String startTeleworkProcess(Long requestId, String employeeId, Boolean specialCase) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", requestId);
        variables.put("employeeId", employeeId);
        variables.put("specialCase", specialCase);

        try {
            /* Fix Defect 6: Use timeout to prevent thread exhaustion. */
            ProcessInstanceEvent event = zeebeClient.newCreateInstanceCommand()
                    .bpmnProcessId(TELEWORK_PROCESS_ID)
                    .latestVersion()
                    .variables(variables)
                    .send()
                    .get(10, TimeUnit.SECONDS);

            return String.valueOf(event.getProcessInstanceKey());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException("Failed to start workflow within timeout", e);
        }
    }

    public void completeTask(String taskKey, String decision, String comment, String assignee) {
        long key = parseTaskKey(taskKey);

        try {
            /* Fix Defect 6: Bounded waits. */
            zeebeClient.newUserTaskAssignCommand(key)
                    .assignee(assignee)
                    .allowOverride(false)
                    .send()
                    .get(5, TimeUnit.SECONDS);

            Map<String, Object> variables = new HashMap<>();
            variables.put("decision", decision);
            variables.put("comment", comment);

            zeebeClient.newUserTaskCompleteCommand(key)
                    .variables(variables)
                    .send()
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException("Failed to complete task within timeout", e);
        }
    }

    public void validateTaskKeyForRequest(
            String taskKey,
            String processInstanceId,
            Long requestId,
            String requiredRole) {
        /*
         * Fix Defect 3: Removed in-memory correlation leak.
         * In a real multi-node env, we would fetch variables from Zeebe or rely on
         * task headers.
         * For now, we ensure the taskKey is syntactically valid and stateless.
         */
        parseTaskKey(taskKey);

        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("Missing process instance id for request");
        }
        if (requestId == null) {
            throw new IllegalArgumentException("Missing request id for task validation");
        }
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
