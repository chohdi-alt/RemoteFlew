package tn.pi.remoteflowapplication.document;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ALFRESCO_IT", matches = "true")
class RejectionAlfrescoIT {

    @Autowired
    private AlfrescoDocumentService documentService;

    @Autowired
    private ManagerApprovalHandler managerApprovalHandler;

    @MockBean
    private TeleworkRequestRepository repository;

    @MockBean
    private CamundaWorkflowService camundaWorkflowService;

    @Value("${alfresco.rejected-folder-id}")
    private String rejectedFolderId;

    private String uploadedNodeId;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerRejectionMovesDocumentToRejectedFolder() throws Exception {
        authenticateAs("manager-1", "ROLE_MANAGER");
        // Test logic...
    }

    private void authenticateAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "n/a",
                        List.of(new SimpleGrantedAuthority(role))));
    }
}
