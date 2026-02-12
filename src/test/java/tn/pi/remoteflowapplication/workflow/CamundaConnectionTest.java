package tn.pi.remoteflowapplication.workflow;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.Topology;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CamundaConnectionTest {

    @Autowired
    private ZeebeClient zeebeClient;

    @Test
    public void testZeebeConnection() {
        // Request the cluster topology to verify connection
        Topology topology = zeebeClient.newTopologyRequest().send().join();

        System.out.println("Connected to Zeebe Cluster: " + topology);

        // Assert that we have at least one broker connected
        assertThat(topology.getBrokers()).isNotEmpty();
    }
}
