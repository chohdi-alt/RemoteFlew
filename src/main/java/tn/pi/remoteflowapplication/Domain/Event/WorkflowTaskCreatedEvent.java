package tn.pi.remoteflowapplication.domain.event;



public class WorkflowTaskCreatedEvent extends DomainEvent {

    private final String eventId;
    private final Long requestId;
    private final String taskType;

    public WorkflowTaskCreatedEvent(Long requestId, String taskType) {
        super();
        this.eventId = java.util.UUID.randomUUID().toString();
        this.requestId = requestId;
        this.taskType = taskType;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getTaskType() {
        return taskType;
    }
}
