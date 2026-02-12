package tn.pi.remoteflowapplication.document;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.application.service.DomainEventPublisher;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.domain.rule.QuotaValidationRule;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.config.location=file:src/main/resources/application.properties",
        "spring.datasource.url=jdbc:h2:mem:manager-alfresco-it;DB_CLOSE_DELAY=-1;MODE=MariaDB",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class ManagerApprovalAlfrescoIT {

    @Autowired
    private AlfrescoDocumentService documentService;

    @Autowired
    private ManagerApprovalHandler managerApprovalHandler;

    @MockBean
    private TeleworkRequestRepository repository;

    @MockBean
    private QuotaValidationRule quotaValidationRule;

    @MockBean
    private CamundaWorkflowService camundaWorkflowService;

    @MockBean
    private DomainEventPublisher domainEventPublisher;

    @Value("${alfresco.approved-folder-id}")
    private String approvedFolderId;

    private String uploadedNodeId;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (uploadedNodeId != null) {
            try {
                documentService.moveToRejected(uploadedNodeId);
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    @Test
    void managerApprovalMovesDocumentToApprovedFolder() throws Exception {
        authenticateAsManager("manager-1");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manager-approval-" + UUID.randomUUID() + ".txt",
                "text/plain",
                "approval-test".getBytes()
        );

        uploadedNodeId = documentService.upload(file);

        TeleworkRequest request = TeleworkRequest.create(
                "employee-1",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 2)
        );
        request.attachJustificatif(uploadedNodeId);
        request.linkProcess("proc-1");

        when(repository.findById(1L)).thenReturn(Optional.of(request));
        when(quotaValidationRule.isSpecialCase(request)).thenReturn(false);

        managerApprovalHandler.approve(
                1L,
                "100",
                new ApprovalDecisionDTO(1L, "manager-1", "ok")
        );

        String parentId = documentService.getParentId(uploadedNodeId);
        assertThat(parentId).isEqualTo(approvedFolderId);
    }

    private void authenticateAsManager(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "n/a",
                        List.of(() -> "ROLE_MANAGER")
                )
        );
    }
}
