package tn.pi.remoteflowapplication.infrastructure.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import tn.pi.remoteflowapplication.domain.exception.WorkflowExecutionException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CamundaWorkflowService implements WorkflowOrchestrationPort {

    private static final String TELEWORK_PROCESS_ID = "telework_process";

    private final ZeebeClient zeebeClient;
    private final RestTemplate tasklistRestTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${camunda.tasklist.base-url:http://localhost:8086}")
    private String tasklistBaseUrl;

    @Value("${camunda.tasklist.username:demo}")
    private String tasklistUsername;

    @Value("${camunda.tasklist.password:demo}")
    private String tasklistPassword;

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
    public void completeTask(String taskKey, String decision, String comment, String assignee) {
        long key = parseTaskKey(taskKey);
        Map<String, Object> variables = new HashMap<>();
        variables.put("decision", decision);
        variables.put("managerComment", comment);

        try {
            zeebeClient.newUserTaskCompleteCommand(key)
                    .variables(variables)
                    .send()
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new WorkflowExecutionException("Failed to complete task " + taskKey, e);
        }
    }

    @Override
    public void validateTaskKeyForRequest(
            String taskKey,
            String processInstanceId,
            Long requestId,
            String requiredRole) {

        parseTaskKey(taskKey);

        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("Missing process instance id for request");
        }

        try {
            String url = tasklistBaseUrl + "/v1/tasks/" + taskKey;
            HttpEntity<Void> request = new HttpEntity<>(tasklistHeaders());
            ResponseEntity<Map<String, Object>> response = tasklistRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            Map<String, Object> task = response.getBody();
            if (task == null) {
                throw new tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException(
                        "Task not found: " + taskKey);
            }

            String taskProcessInstanceId = String.valueOf(task.get("processInstanceKey"));
            if (!processInstanceId.equals(taskProcessInstanceId)) {
                throw new tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException(
                        "Task does not belong to the given process instance");
            }
        } catch (Exception e) {
            throw new tn.pi.remoteflowapplication.domain.exception.ForbiddenOperationException(
                    "Task validation failed: " + e.getMessage());
        }
    }

    @Override
    public Page<WorkflowPendingTaskDTO> findPendingTasksByCandidateGroup(String candidateGroup, Pageable pageable) {
        List<Map<String, Object>> tasks = queryTasklist(candidateGroup);
        List<WorkflowPendingTaskDTO> content = new ArrayList<>();

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), tasks.size());

        for (int i = start; i < end; i++) {
            Map<String, Object> task = tasks.get(i);
            content.add(new WorkflowPendingTaskDTO(
                    String.valueOf(task.get("id")),
                    null, // requestId is not easily available without fetching variables
                    String.valueOf(task.get("processInstanceKey")),
                    candidateGroup));
        }

        return new PageImpl<>(content, pageable, tasks.size());
    }

    @Override
    public long countPendingTasksByCandidateGroup(String candidateGroup) {
        try {
            String searchUrl = tasklistBaseUrl + "/v1/tasks/search";
            Map<String, Object> body = new HashMap<>();
            body.put("state", "CREATED");
            body.put("candidateGroup", candidateGroup);
            body.put("pageSize", 1);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, tasklistHeaders());
            ResponseEntity<String> response = tasklistRestTemplate.exchange(
                    searchUrl,
                    HttpMethod.POST,
                    request,
                    String.class);

            String raw = response.getBody();
            if (raw == null || raw.isBlank()) {
                return 0L;
            }

            JsonNode root = objectMapper.readTree(raw);
            if (root.isObject()) {
                JsonNode countNode = root.get("total") != null ? root.get("total") : root.get("count");
                if (countNode == null)
                    countNode = root.get("totalCount");

                if (countNode != null && countNode.isNumber()) {
                    return countNode.asLong();
                }

                JsonNode itemsNode = root.get("items");
                if (itemsNode != null && itemsNode.isArray()) {
                    return itemsNode.size();
                }
            }

            if (root.isArray()) {
                return root.size();
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private List<Map<String, Object>> queryTasklist(String candidateGroup) {
        String url = tasklistBaseUrl + "/v1/tasks/search";

        Map<String, Object> body = new HashMap<>();
        body.put("state", "CREATED");
        body.put("candidateGroup", candidateGroup);
        body.put("pageSize", 500);

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, tasklistHeaders());
            ResponseEntity<List<Map<String, Object>>> response = tasklistRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            return response.getBody() != null ? response.getBody() : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private HttpHeaders tasklistHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(tasklistUsername, tasklistPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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
