package tn.pi.remoteflowapplication.infrastructure.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.port.out.WorkflowOrchestrationPort;

import java.util.Map;

@Service
@ConditionalOnProperty(
    value = "camunda.client.zeebe.enabled",
    havingValue = "false"
)
public class NoOpWorkflowService implements WorkflowOrchestrationPort, WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(NoOpWorkflowService.class);

    @Override
    public String startTeleworkProcess(Long requestId, String employeeId, Boolean specialCase) {
        log.info("Workflow disabled - using NoOpWorkflowService");
        return "noop-" + requestId;
    }

    @Override
    public void completeTask(String taskKey, Map<String, Object> variables) {
        log.info("Workflow disabled - using NoOpWorkflowService");
    }

    @Override
    public void completeTask(String taskKey, String decision, String comment, String assignee) {
        log.info("Workflow disabled - using NoOpWorkflowService");
    }
}
