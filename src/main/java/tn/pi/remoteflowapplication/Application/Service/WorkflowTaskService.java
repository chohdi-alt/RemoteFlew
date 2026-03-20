package tn.pi.remoteflowapplication.application.service;

import tn.pi.remoteflowapplication.domain.entity.TaskEntity;

import java.util.List;
import java.util.Optional;

public interface WorkflowTaskService {
    TaskEntity createTask(Long requestId, String type, Long jobKey);
    List<TaskEntity> getPendingTasksForManager();
    List<TaskEntity> getPendingTasksForHr();
    Optional<TaskEntity> getPendingTaskForRequest(Long requestId, String type);
    TaskEntity validateAndGetTask(Long taskId, Long requestId, String type);
    long countPendingTasksForManager();
    long countPendingTasksForHr();
    void completeTask(Long taskId, String assignedTo);
    void completeTaskByJobKey(Long jobKey, String assignedTo);
}
