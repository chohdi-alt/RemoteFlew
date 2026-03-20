package tn.pi.remoteflowapplication.application.port.out;

import java.util.HashMap;
import java.util.Map;

public interface WorkflowOrchestrationPort {
    String startTeleworkProcess(Long requestId, String employeeId, Boolean specialCase);

    void completeTask(String taskKey, Map<String, Object> variables);

    default void completeTask(String taskKey, String decision, String comment, String assignee) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("decision", decision);
        if (comment != null) {
            variables.put("managerComment", comment);
        }
        completeTask(taskKey, variables);
    }

}
