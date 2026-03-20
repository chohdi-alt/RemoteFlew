package tn.pi.remoteflowapplication.infrastructure.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.pi.remoteflowapplication.application.service.WorkflowTaskService;

@Component
public class TeleworkZeebeWorker {

    private static final Logger logger = LoggerFactory.getLogger(TeleworkZeebeWorker.class);

    private final WorkflowTaskService workflowTaskService;

    public TeleworkZeebeWorker(WorkflowTaskService workflowTaskService) {
        this.workflowTaskService = workflowTaskService;
    }

    @JobWorker(
            type = "manager-approval",
            autoComplete = false,
            fetchAllVariables = true,
            timeout = 86400000L,
            requestTimeout = 10000L,
            pollInterval = 100L,
            maxJobsActive = 1,
            streamEnabled = false)
    public void handleManagerApproval(ActivatedJob job) {
        Long requestId = extractRequestId(job);
        if (requestId != null) {
            logger.info("Manager approval task created in DB for request={}, jobKey={}", requestId, job.getKey());
            workflowTaskService.createTask(requestId, "MANAGER", job.getKey());
        } else {
            logger.error("Failed to extract requestId from job variables for manager-approval, jobKey={}", job.getKey());
        }
        // Deliberately NOT completing the job here. The user triggers completion.
    }

    @JobWorker(
            type = "hr-approval",
            autoComplete = false,
            fetchAllVariables = true,
            timeout = 86400000L,
            requestTimeout = 10000L,
            pollInterval = 100L,
            maxJobsActive = 1,
            streamEnabled = false)
    public void handleHrApproval(ActivatedJob job) {
        Long requestId = extractRequestId(job);
        if (requestId != null) {
            logger.info("HR approval task created in DB for request={}, jobKey={}", requestId, job.getKey());
            workflowTaskService.createTask(requestId, "HR", job.getKey());
        } else {
            logger.error("Failed to extract requestId from job variables for hr-approval, jobKey={}", job.getKey());
        }
        // Deliberately NOT completing the job here. The user triggers completion.
    }

    private Long extractRequestId(ActivatedJob job) {
        try {
            Object reqIdObj = job.getVariablesAsMap().get("requestId");
            if (reqIdObj instanceof Number num) {
                return num.longValue();
            } else if (reqIdObj instanceof String str) {
                return Long.parseLong(str);
            }
        } catch (Exception e) {
            logger.error("Error extracting requestId from job variables", e);
        }
        return null;
    }
}
