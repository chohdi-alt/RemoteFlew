package tn.pi.remoteflowapplication.application.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.pi.remoteflowapplication.application.service.WorkflowTaskService;
import tn.pi.remoteflowapplication.domain.entity.TaskEntity;
import tn.pi.remoteflowapplication.domain.exception.BusinessException;
import tn.pi.remoteflowapplication.infrastructure.persistence.SpringTaskJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class WorkflowTaskServiceImpl implements WorkflowTaskService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskServiceImpl.class);

    private final SpringTaskJpaRepository taskRepository;

    public WorkflowTaskServiceImpl(SpringTaskJpaRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    @Transactional
    public TaskEntity createTask(Long requestId, String type, Long jobKey) {
        if (jobKey == null) {
            throw new BusinessException("Cannot create workflow task without Zeebe job key");
        }

        if (taskRepository.existsByJobKey(jobKey)) {
            return taskRepository.findByJobKey(jobKey)
                    .orElseThrow(() -> new BusinessException("Task exists but cannot be loaded for job key: " + jobKey));
        }

        TaskEntity task = new TaskEntity(requestId, jobKey, type, "PENDING", Instant.now());
        try {
            return taskRepository.save(task);
        } catch (DataIntegrityViolationException ex) {
            return taskRepository.findByJobKey(jobKey).orElseThrow(() -> ex);
        }
    }

    @Override
    public List<TaskEntity> getPendingTasksForManager() {
        return taskRepository.findByTypeAndStatus("MANAGER", "PENDING");
    }

    @Override
    public List<TaskEntity> getPendingTasksForHr() {
        return taskRepository.findByTypeAndStatus("HR", "PENDING");
    }

    @Override
    public Optional<TaskEntity> getPendingTaskForRequest(Long requestId, String type) {
        return taskRepository.findByRequestIdAndTypeAndStatus(requestId, type, "PENDING");
    }

    @Override
    public TaskEntity validateAndGetTask(Long taskId, Long requestId, String type) {
        return taskRepository.findById(taskId)
            .filter(task -> task.getRequestId().equals(requestId))
            .filter(task -> task.getType().equals(type))
            .filter(task -> task.getStatus().equals("PENDING"))
            .orElseThrow(() -> new BusinessException("Valid pending task not found for the given request"));
    }

    @Override
    public long countPendingTasksForManager() {
        return taskRepository.countByTypeAndStatus("MANAGER", "PENDING");
    }

    @Override
    public long countPendingTasksForHr() {
        return taskRepository.countByTypeAndStatus("HR", "PENDING");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTask(Long taskId, String assignedTo) {
        taskRepository.findById(taskId).ifPresentOrElse(task -> {
            logger.debug("Completing task by id. taskId={} currentStatus={} assignedTo={}", taskId, task.getStatus(), assignedTo);
            task.setStatus("COMPLETED");
            task.setAssignedTo(assignedTo);
            taskRepository.saveAndFlush(task);
            logger.debug("Task completion flushed. taskId={} newStatus={}", taskId, task.getStatus());
        }, () -> logger.warn("Task not found for completion by id. taskId={}", taskId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTaskByJobKey(Long jobKey, String assignedTo) {
        taskRepository.findByJobKey(jobKey).ifPresentOrElse(task -> {
            logger.debug("Completing task by jobKey. taskId={} jobKey={} currentStatus={} assignedTo={}",
                    task.getId(), jobKey, task.getStatus(), assignedTo);
            task.setStatus("COMPLETED");
            task.setAssignedTo(assignedTo);
            taskRepository.saveAndFlush(task);
            logger.debug("Task completion by jobKey flushed. taskId={} jobKey={} newStatus={}",
                    task.getId(), jobKey, task.getStatus());
        }, () -> logger.warn("Task not found for completion by jobKey. jobKey={}", jobKey));
    }
}
