package tn.pi.remoteflowapplication.application.dto;

public record WorkflowPendingTaskDTO(
        String taskKey,
        Long requestId,
        String processInstanceId,
        String candidateGroup
) {
}

