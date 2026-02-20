package tn.pi.remoteflowapplication.application.port.out;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.pi.remoteflowapplication.application.dto.WorkflowPendingTaskDTO;

public interface WorkflowOrchestrationPort {
    String startTeleworkProcess(Long requestId, String employeeId, Boolean specialCase);

    void completeTask(String taskKey, String decision, String comment, String assignee);

    void validateTaskKeyForRequest(String taskKey, String processInstanceId, Long requestId, String requiredRole);

    Page<WorkflowPendingTaskDTO> findPendingTasksByCandidateGroup(String candidateGroup, Pageable pageable);
}
