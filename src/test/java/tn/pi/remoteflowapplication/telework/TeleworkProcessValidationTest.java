package tn.pi.remoteflowapplication.telework;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architectural Proof of Concept (PoC) Test.
 * Validates that the BPMN process definition is syntactically correct and
 * loadable.
 * This addresses the "Test Coherence" gap by verifying the artifact itself.
 */
class TeleworkProcessValidationTest {

    private static final String BPMN_PATH = "bpmn/telework_process.bpmn";

    @Test
    void shouldLoadAndParseBpmnProcess() throws Exception {
        // Given
        ClassPathResource resource = new ClassPathResource(BPMN_PATH);
        assertTrue(resource.exists(), "BPMN file must exist at " + BPMN_PATH);

        try (InputStream inputStream = resource.getInputStream()) {
            // When
            BpmnModelInstance modelInstance = Bpmn.readModelFromStream(inputStream);

            // Then
            assertNotNull(modelInstance, "BPMN model should be parsed successfully");

            // Validate key elements exist using Camunda Model API
            assertNotNull(modelInstance.getModelElementById("telework_process"),
                    "Process ID 'telework_process' must exist");
            assertNotNull(modelInstance.getModelElementById("StartEvent_1"), "Start event must exist");
        }
    }
}
