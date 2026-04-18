package tn.pi.remoteflowapplication.infrastructure.workflow;

import java.util.Map;

public interface WorkflowService {
    String startTeleworkProcess(Long requestId, String employeeId, Boolean specialCase);
    void completeTask(String taskKey, Map<String, Object> variables);
    void completeTask(String taskKey, String decision, String comment, String assignee);
}
