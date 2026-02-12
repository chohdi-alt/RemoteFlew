package tn.pi.remoteflowapplication.application.port.out;

public interface WorkflowOrchestrationPort {
    String startTeleworkProcess(Long requestId, String employeeId, Boolean specialCase);

    void completeTask(String taskKey, String decision, String comment, String assignee);

    void validateTaskKeyForRequest(String taskKey, String processInstanceId, Long requestId, String requiredRole);
}
