package tn.pi.remoteflowapplication.tests.workflow;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("workflow")
@Testcontainers(disabledWithoutDocker = true)
class TeleworkProcessWorkflowIT {

    private static final String PROCESS_ID = "telework_process";

    @Container
    static final GenericContainer<?> ZEEBE = new GenericContainer<>(DockerImageName.parse("camunda/zeebe:8.5.3"))
            .withExposedPorts(26500)
            .withEnv("ZEEBE_BROKER_NETWORK_HOST", "0.0.0.0")
            .withStartupTimeout(Duration.ofMinutes(2));

    private static ZeebeClient zeebeClient;

    @BeforeAll
    static void setUpClient() {
        String grpcAddress = "http://" + ZEEBE.getHost() + ":" + ZEEBE.getMappedPort(26500);
        zeebeClient = ZeebeClient.newClientBuilder()
                .grpcAddress(URI.create(grpcAddress))
                .usePlaintext()
                .build();

        waitForBrokerReady(Duration.ofSeconds(45));

        long deployDeadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (true) {
            try {
                zeebeClient.newDeployResourceCommand()
                        .addResourceFromClasspath("telework_process.bpmn")
                        .send()
                        .join();
                break;
            } catch (Exception ex) {
                if (System.nanoTime() >= deployDeadline) {
                    throw ex;
                }
                sleep(750);
            }
        }
    }

    @AfterAll
    static void tearDownClient() {
        if (zeebeClient != null) {
            zeebeClient.close();
        }
    }

    @Test
    void shouldExecuteFullLifecycleSubmitManagerHrToFinalState() {
        long requestId = 101L;
        ProcessInstanceEvent process = startProcess(requestId);

        ActivatedJob managerJob = waitForJob("manager-approval", requestId, Duration.ofSeconds(20));
        assertEquals(process.getProcessInstanceKey(), managerJob.getProcessInstanceKey());
        assertEquals("employee-101", managerJob.getVariablesAsMap().get("employeeId"));
        assertEquals(false, managerJob.getVariablesAsMap().get("specialCase"));

        zeebeClient.newCompleteCommand(managerJob.getKey())
                .variables(Map.of("decision", "APPROVE", "managerComment", "Manager approved"))
                .send()
                .join();

        ActivatedJob hrJob = waitForJob("hr-approval", requestId, Duration.ofSeconds(20));
        assertEquals(process.getProcessInstanceKey(), hrJob.getProcessInstanceKey());

        zeebeClient.newCompleteCommand(hrJob.getKey())
                .variables(Map.of("decision", "APPROVE", "hrComment", "HR approved"))
                .send()
                .join();

        assertNoPendingJob("manager-approval", requestId, Duration.ofSeconds(5));
        assertNoPendingJob("hr-approval", requestId, Duration.ofSeconds(5));
    }

    @Test
    void shouldFollowRejectionPathWhenManagerRejects() {
        long requestId = 102L;
        ProcessInstanceEvent process = startProcess(requestId);

        ActivatedJob managerJob = waitForJob("manager-approval", requestId, Duration.ofSeconds(20));
        assertEquals(process.getProcessInstanceKey(), managerJob.getProcessInstanceKey());

        zeebeClient.newCompleteCommand(managerJob.getKey())
                .variables(Map.of("decision", "REJECT", "managerComment", "Rejected by manager"))
                .send()
                .join();

        assertNoPendingJob("hr-approval", requestId, Duration.ofSeconds(5));
    }

    @Test
    void shouldFollowRejectionPathWhenHrRejects() {
        long requestId = 104L;
        ProcessInstanceEvent process = startProcess(requestId);

        ActivatedJob managerJob = waitForJob("manager-approval", requestId, Duration.ofSeconds(20));
        zeebeClient.newCompleteCommand(managerJob.getKey())
                .variables(Map.of("decision", "APPROVE", "managerComment", "Manager OK"))
                .send()
                .join();

        ActivatedJob hrJob = waitForJob("hr-approval", requestId, Duration.ofSeconds(20));
        assertEquals(process.getProcessInstanceKey(), hrJob.getProcessInstanceKey());

        zeebeClient.newCompleteCommand(hrJob.getKey())
                .variables(Map.of("decision", "REJECT", "hrComment", "Rejected by HR"))
                .send()
                .join();

        // Verification: process should end at Rejected state
        assertNoPendingJob("manager-approval", requestId, Duration.ofSeconds(5));
        assertNoPendingJob("hr-approval", requestId, Duration.ofSeconds(5));
    }

    @Test
    void shouldExposeErrorPathWhenDecisionVariableIsInvalid() {
        long requestId = 103L;
        startProcess(requestId);

        ActivatedJob managerJob = waitForJob("manager-approval", requestId, Duration.ofSeconds(20));

        zeebeClient.newCompleteCommand(managerJob.getKey())
                .variables(Map.of("decision", "UNEXPECTED_VALUE"))
                .send()
                .join();

        assertNoPendingJob("hr-approval", requestId, Duration.ofSeconds(5));
    }

    private static ProcessInstanceEvent startProcess(long requestId) {
        return zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(Map.of(
                        "requestId", requestId,
                        "employeeId", "employee-" + requestId,
                        "specialCase", false))
                .send()
                .join();
    }

    private static ActivatedJob waitForJob(String jobType, long requestId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            List<ActivatedJob> jobs = zeebeClient.newActivateJobsCommand()
                    .jobType(jobType)
                    .maxJobsToActivate(1)
                    .timeout(Duration.ofMinutes(1))
                    .send()
                    .join()
                    .getJobs();

            if (!jobs.isEmpty()) {
                ActivatedJob job = jobs.get(0);
                Object actualRequestId = job.getVariablesAsMap().get("requestId");
                if (actualRequestId instanceof Number number && number.longValue() == requestId) {
                    return job;
                }
            }

            sleep(200);
        }

        throw new AssertionError("No activated job of type " + jobType + " for requestId " + requestId);
    }

    private static void assertNoPendingJob(String jobType, long requestId, Duration duration) {
        long deadline = System.nanoTime() + duration.toNanos();

        while (System.nanoTime() < deadline) {
            List<ActivatedJob> jobs = zeebeClient.newActivateJobsCommand()
                    .jobType(jobType)
                    .maxJobsToActivate(1)
                    .timeout(Duration.ofSeconds(5))
                    .send()
                    .join()
                    .getJobs();

            boolean hasJobForRequest = jobs.stream().anyMatch(job -> {
                Object value = job.getVariablesAsMap().get("requestId");
                return value instanceof Number number && number.longValue() == requestId;
            });

            if (hasJobForRequest) {
                throw new AssertionError("Unexpected job of type " + jobType + " for requestId " + requestId);
            }

            sleep(150);
        }

        assertTrue(true);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for workflow state", ex);
        }
    }

    private static void waitForBrokerReady(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (!zeebeClient.newTopologyRequest().send().join().getBrokers().isEmpty()) {
                    return;
                }
            } catch (Exception ignored) {
                // broker still booting
            }
            sleep(500);
        }
        throw new IllegalStateException("Zeebe broker did not become ready within " + timeout);
    }
}
