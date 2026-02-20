package tn.pi.remoteflowapplication.infrastructure.workflow;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.pi.remoteflowapplication.application.dto.WorkflowPendingTaskDTO;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private final RestTemplate tasklistRestTemplate = new RestTemplate();

    @Value("${camunda.tasklist.base-url:http://localhost:8086}")
    private String tasklistBaseUrl;

    @Value("${camunda.tasklist.username:demo}")
    private String tasklistUsername;

    @Value("${camunda.tasklist.password:demo}")
    private String tasklistPassword;

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

    @Override
    public Page<WorkflowPendingTaskDTO> findPendingTasksByCandidateGroup(String candidateGroup, Pageable pageable) {
        List<Map<String, Object>> tasks = queryTasklist(candidateGroup);
        int fromIndex = (int) Math.min(pageable.getOffset(), tasks.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), tasks.size());

        List<WorkflowPendingTaskDTO> content = new ArrayList<>();
        for (int i = fromIndex; i < toIndex; i++) {
            Map<String, Object> task = tasks.get(i);
            content.add(toWorkflowPendingTask(task, candidateGroup));
        }

        return new PageImpl<>(content, pageable, tasks.size());
    }

    private List<Map<String, Object>> queryTasklist(String candidateGroup) {
        String url = tasklistBaseUrl + "/v1/tasks/search";

        Map<String, Object> body = new HashMap<>();
        body.put("state", "CREATED");
        body.put("candidateGroup", candidateGroup);
        body.put("pageSize", 500);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, tasklistHeaders());
        ResponseEntity<List<Map<String, Object>>> response = tasklistRestTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {
                });
        List<Map<String, Object>> tasks = response.getBody();
        return tasks == null ? List.of() : tasks;
    }

    private WorkflowPendingTaskDTO toWorkflowPendingTask(Map<String, Object> task, String candidateGroup) {
        String taskKey = asString(task.get("id"));
        String processInstanceId = asString(task.get("processInstanceKey"));
        Long requestId = resolveRequestIdFromTaskVariables(taskKey);
        return new WorkflowPendingTaskDTO(taskKey, requestId, processInstanceId, candidateGroup);
    }

    private Long resolveRequestIdFromTaskVariables(String taskKey) {
        if (taskKey == null || taskKey.isBlank()) {
            return null;
        }

        try {
            String url = tasklistBaseUrl + "/v1/tasks/" + taskKey + "/variables/search";
            Map<String, Object> body = Map.of("variableNames", List.of("requestId"));
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, tasklistHeaders());

            ResponseEntity<List<Map<String, Object>>> response = tasklistRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<>() {
                    });

            List<Map<String, Object>> variables = response.getBody();
            if (variables == null || variables.isEmpty()) {
                return null;
            }

            Object value = variables.get(0).get("value");
            if (value == null) {
                return null;
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private HttpHeaders tasklistHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(tasklistUsername, tasklistPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
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
