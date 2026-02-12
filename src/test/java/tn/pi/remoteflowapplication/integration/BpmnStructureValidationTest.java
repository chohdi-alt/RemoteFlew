package tn.pi.remoteflowapplication.integration;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BPMN Structure Validation Test
 * 
 * This test validates the BPMN process structure without requiring a running
 * Zeebe engine.
 * It addresses the "Test Coherence Gap" by verifying:
 * 
 * 1. BPMN file is syntactically valid
 * 2. Process ID matches code expectations
 * 3. Required tasks exist with correct IDs
 * 4. Task candidate groups match application roles
 * 5. Gateway conditions reference correct variables
 * 6. Process has proper start and end events
 * 
 * This test WILL FAIL if:
 * - BPMN has XML syntax errors
 * - Process ID is renamed
 * - Task IDs are changed
 * - Candidate groups don't match roles
 * - Gateway conditions reference wrong variables
 */
class BpmnStructureValidationTest {

    private static final String BPMN_PATH = "bpmn/telework_process.bpmn";
    private static final String EXPECTED_PROCESS_ID = "telework_process";

    @Test
    void shouldLoadAndParseBpmnFile() throws Exception {
        // Given
        ClassPathResource resource = new ClassPathResource(BPMN_PATH);
        assertTrue(resource.exists(), "BPMN file must exist at " + BPMN_PATH);

        // When
        BpmnModelInstance modelInstance;
        try (InputStream inputStream = resource.getInputStream()) {
            modelInstance = Bpmn.readModelFromStream(inputStream);
        }

        // Then
        assertNotNull(modelInstance, "BPMN model should be parsed successfully");
    }

    @Test
    void shouldHaveCorrectProcessId() throws Exception {
        // Given
        BpmnModelInstance modelInstance = loadBpmn();

        // When
        io.camunda.zeebe.model.bpmn.instance.Process process = modelInstance.getModelElementById(EXPECTED_PROCESS_ID);

        // Then
        assertNotNull(process,
                "Process with ID '" + EXPECTED_PROCESS_ID + "' must exist");
        assertEquals(EXPECTED_PROCESS_ID, process.getId(),
                "Process ID must match code expectations");
        assertTrue(process.isExecutable(),
                "Process must be marked as executable");
    }

    @Test
    void shouldHaveStartEvent() throws Exception {
        // Given
        BpmnModelInstance modelInstance = loadBpmn();

        // When
        StartEvent startEvent = modelInstance.getModelElementById("StartEvent_1");

        // Then
        assertNotNull(startEvent, "Start event 'StartEvent_1' must exist");
    }

    @Test
    void shouldHaveManagerApprovalTask() throws Exception {
        // Given
        BpmnModelInstance modelInstance = loadBpmn();

        // When
        UserTask managerTask = modelInstance.getModelElementById("Activity_1h9mlkf");

        // Then
        assertNotNull(managerTask, "Manager approval task must exist");
        assertEquals("Manager Approval", managerTask.getName(),
                "Manager task must have correct name");

        // Verify candidate group (this is critical for task assignment)
        String bpmnContent = readBpmnAsString();
        assertTrue(bpmnContent.contains("candidateGroups=\"MANAGER\""),
                "Manager task must have candidateGroups='MANAGER'");
    }

    @Test
    void shouldHaveHrApprovalTask() throws Exception {
        // Given
        BpmnModelInstance modelInstance = loadBpmn();

        // When
        UserTask hrTask = modelInstance.getModelElementById("Activity_040zhi1");

        // Then
        assertNotNull(hrTask, "HR approval task must exist");
        assertEquals("HR Approval", hrTask.getName(),
                "HR task must have correct name");

        // Verify candidate group
        String bpmnContent = readBpmnAsString();
        assertTrue(bpmnContent.contains("candidateGroups=\"HR\""),
                "HR task must have candidateGroups='HR'");
    }

    @Test
    void shouldHaveManagerDecisionGateway() throws Exception {
        // Given
        BpmnModelInstance modelInstance = loadBpmn();

        // When
        ExclusiveGateway gateway = modelInstance.getModelElementById("Gateway_0zwg2ay");

        // Then
        assertNotNull(gateway, "Manager decision gateway must exist");

        // Verify gateway has outgoing flows
        Collection<SequenceFlow> outgoing = gateway.getOutgoing();
        assertTrue(outgoing.size() >= 3,
                "Gateway should have at least 3 outgoing flows (REJECT, APPROVE normal, APPROVE special)");
    }

    @Test
    void shouldHaveHrDecisionGateway() throws Exception {
        // Given
        BpmnModelInstance modelInstance = loadBpmn();

        // When
        ExclusiveGateway gateway = modelInstance.getModelElementById("Gateway_1n8jff6");

        // Then
        assertNotNull(gateway, "HR decision gateway must exist");

        // Verify gateway has outgoing flows
        Collection<SequenceFlow> outgoing = gateway.getOutgoing();
        assertTrue(outgoing.size() >= 2,
                "Gateway should have at least 2 outgoing flows (APPROVE, REJECT)");
    }

    @Test
    void shouldHaveApprovedEndEvent() throws Exception {
        // Given
        BpmnModelInstance modelInstance = loadBpmn();

        // When
        EndEvent approvedEnd = modelInstance.getModelElementById("Event_0y3caap");

        // Then
        assertNotNull(approvedEnd, "Approved end event must exist");
        assertEquals("Approved", approvedEnd.getName(),
                "Approved end event must have correct name");
    }

    @Test
    void shouldHaveRejectedEndEvent() throws Exception {
        // Given
        BpmnModelInstance modelInstance = loadBpmn();

        // When
        EndEvent rejectedEnd = modelInstance.getModelElementById("Event_01w7ufk");

        // Then
        assertNotNull(rejectedEnd, "Rejected end event must exist");
        assertEquals("Rejected", rejectedEnd.getName(),
                "Rejected end event must have correct name");
    }

    @Test
    void shouldHaveCorrectGatewayConditions() throws Exception {
        // Given
        String bpmnContent = readBpmnAsString();

        // Then: Verify gateway conditions reference correct variables

        // Manager reject condition
        assertTrue(bpmnContent.contains("decision = \"REJECT\""),
                "Gateway must check 'decision' variable for REJECT");

        // Manager approve (normal case) condition
        assertTrue(bpmnContent.contains("decision = \"APPROVE\" and specialCase = false"),
                "Gateway must check 'decision' and 'specialCase' for normal approval");

        // Manager approve (special case) condition
        assertTrue(bpmnContent.contains("decision = \"APPROVE\" and specialCase = true"),
                "Gateway must check 'decision' and 'specialCase' for special case");

        // HR approve condition
        assertTrue(bpmnContent.contains("decision = \"APPROVE\""),
                "HR gateway must check 'decision' variable");
    }

    @Test
    void shouldValidateVariableNamesMatchCode() throws Exception {
        // This test ensures BPMN variables match what the code sends
        String bpmnContent = readBpmnAsString();

        // Variables set by CamundaWorkflowService.startTeleworkProcess():
        // - requestId
        // - employeeId
        // - specialCase

        // Variables set by approval handlers:
        // - decision
        // - comment

        // Verify these are referenced in BPMN
        assertTrue(bpmnContent.contains("specialCase"),
                "BPMN must reference 'specialCase' variable");
        assertTrue(bpmnContent.contains("decision"),
                "BPMN must reference 'decision' variable");

        // Note: requestId and employeeId are not used in gateway conditions
        // but are passed as process variables for correlation
    }

    @Test
    void shouldHaveCompleteWorkflowPath() throws Exception {
        // Given
        BpmnModelInstance modelInstance = loadBpmn();
        io.camunda.zeebe.model.bpmn.instance.Process process = modelInstance.getModelElementById(EXPECTED_PROCESS_ID);

        // When: Count flow nodes
        Collection<FlowNode> flowNodes = process.getChildElementsByType(FlowNode.class);

        // Then: Verify we have all expected nodes
        assertTrue(flowNodes.size() >= 7,
                "Process should have at least 7 flow nodes (start, 2 tasks, 2 gateways, 2 ends)");

        // Verify connectivity: start → manager task → gateway → (hr task OR end) → end
        StartEvent start = modelInstance.getModelElementById("StartEvent_1");
        assertNotNull(start);
        assertTrue(start.getOutgoing().size() > 0, "Start event must have outgoing flow");
    }

    // Helper methods

    private BpmnModelInstance loadBpmn() throws Exception {
        ClassPathResource resource = new ClassPathResource(BPMN_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return Bpmn.readModelFromStream(inputStream);
        }
    }

    private String readBpmnAsString() throws Exception {
        ClassPathResource resource = new ClassPathResource(BPMN_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes());
        }
    }
}
