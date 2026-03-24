package tn.pi.remoteflowapplication.integration;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.Topology;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
class BpmnDecisionRoutingIT {

    @Container
    private static final GenericContainer<?> zeebeContainer = new GenericContainer<>(
            DockerImageName.parse("camunda/zeebe:8.5.3"))
            .withExposedPorts(26500)
            .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
            .withEnv("ZEEBE_BROKER_GATEWAY_NETWORK_HOST", "0.0.0.0")
            .withEnv("ZEEBE_BROKER_GATEWAY_NETWORK_PORT", "26500")
            .withEnv("ZEEBE_BROKER_NETWORK_HOST", "0.0.0.0")
            .withStartupTimeout(Duration.ofMinutes(2));

    private static ZeebeClient client;

    @BeforeAll
    static void setup() {
        String gatewayAddress = zeebeContainer.getHost() + ":" + zeebeContainer.getMappedPort(26500);
        client = ZeebeClient.newClientBuilder()
                .gatewayAddress(gatewayAddress)
                .usePlaintext()
                .build();
        waitForZeebeReady(Duration.ofSeconds(60));
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void managerReject_routesDirectlyToRejectedEnd() throws IOException, InterruptedException {
        deployBpmn();
        startProcess(1001L);

        ActivatedJob managerJob = waitForSingleJob("manager-approval", Duration.ofSeconds(20));
        completeJob(managerJob.getKey(), Map.of("decision", "REJECT"));

        assertNoAvailableJob("hr-approval", Duration.ofSeconds(5));
    }

    @Test
    void managerApprove_routesToHrTask() throws IOException, InterruptedException {
        deployBpmn();
        startProcess(1002L);

        ActivatedJob managerJob = waitForSingleJob("manager-approval", Duration.ofSeconds(20));
        completeJob(managerJob.getKey(), Map.of("decision", "APPROVE"));

        ActivatedJob hrJob = waitForSingleJob("hr-approval", Duration.ofSeconds(20));
        assertNotNull(hrJob);
        assertTrue(hrJob.getKey() > 0);
        completeJob(hrJob.getKey(), Map.of("decision", "REJECT"));
    }

    @Test
    void hrReject_routesToRejectedEnd() throws IOException, InterruptedException {
        deployBpmn();
        startProcess(1003L);

        ActivatedJob managerJob = waitForSingleJob("manager-approval", Duration.ofSeconds(20));
        completeJob(managerJob.getKey(), Map.of("decision", "APPROVE"));

        ActivatedJob hrJob = waitForSingleJob("hr-approval", Duration.ofSeconds(20));
        completeJob(hrJob.getKey(), Map.of("decision", "REJECT"));

        assertNoAvailableJob("manager-approval", Duration.ofSeconds(3));
        assertNoAvailableJob("hr-approval", Duration.ofSeconds(3));
    }

    @Test
    void hrApprove_routesToApprovedEnd() throws IOException, InterruptedException {
        deployBpmn();
        startProcess(1004L);

        ActivatedJob managerJob = waitForSingleJob("manager-approval", Duration.ofSeconds(20));
        completeJob(managerJob.getKey(), Map.of("decision", "APPROVE"));

        ActivatedJob hrJob = waitForSingleJob("hr-approval", Duration.ofSeconds(20));
        completeJob(hrJob.getKey(), Map.of("decision", "APPROVE"));

        assertNoAvailableJob("manager-approval", Duration.ofSeconds(3));
        assertNoAvailableJob("hr-approval", Duration.ofSeconds(3));
    }

    private DeploymentEvent deployBpmn() throws IOException {
        ClassPathResource bpmnResource = new ClassPathResource("telework_process.bpmn");
        try (InputStream bpmnStream = bpmnResource.getInputStream()) {
            return client.newDeployResourceCommand()
                    .addResourceStream(bpmnStream, "telework_process.bpmn")
                    .send()
                    .join();
        }
    }

    private ProcessInstanceEvent startProcess(Long requestId) {
        return client.newCreateInstanceCommand()
                .bpmnProcessId("telework_process")
                .latestVersion()
                .variables(Map.of(
                        "requestId", requestId,
                        "employeeId", "emp-" + requestId,
                        "specialCase", false))
                .send()
                .join();
    }

    private ActivatedJob waitForSingleJob(String type, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<ActivatedJob> jobs = activateJobs(type, Duration.ofSeconds(20), Duration.ofSeconds(1));
            if (!jobs.isEmpty()) {
                return jobs.get(0);
            }
            Thread.sleep(200);
        }
        fail("Timed out waiting for job type: " + type);
        return null;
    }

    private void completeJob(long jobKey, Map<String, Object> variables) {
        client.newCompleteCommand(jobKey)
                .variables(variables)
                .send()
                .join();
    }

    private void assertNoAvailableJob(String type, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<ActivatedJob> jobs = activateJobs(type, Duration.ofSeconds(5), Duration.ofMillis(200));
            if (!jobs.isEmpty()) {
                fail("Unexpected job available for type " + type + " with key " + jobs.get(0).getKey());
            }
            Thread.sleep(100);
        }
    }

    private List<ActivatedJob> activateJobs(String type, Duration activationTimeout, Duration requestTimeout) {
        ActivateJobsResponse response = client.newActivateJobsCommand()
                .jobType(type)
                .maxJobsToActivate(1)
                .timeout(activationTimeout)
                .requestTimeout(requestTimeout)
                .send()
                .join();
        return response.getJobs();
    }

    private static void waitForZeebeReady(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Topology topology = client.newTopologyRequest().send().join();
                boolean healthyPartition = topology.getBrokers().stream()
                        .flatMap(broker -> broker.getPartitions().stream())
                        .anyMatch(partition -> "HEALTHY".equals(partition.getHealth().name()));
                if (healthyPartition) {
                    return;
                }
            } catch (Exception ignored) {
                // retry until timeout
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("Zeebe gateway not ready after " + timeout);
    }
}
