package tn.pi.remoteflowapplication.document;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.pi.remoteflowapplication.application.command.ManagerApprovalHandler;
import tn.pi.remoteflowapplication.application.dto.ApprovalDecisionDTO;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;
import tn.pi.remoteflowapplication.infrastructure.workflow.CamundaWorkflowService;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = RejectionAlfrescoIT.TestConfig.class,
        properties = {
                "spring.config.location=file:src/main/resources/application.properties",
                "spring.datasource.url=jdbc:h2:mem:rejection-alfresco-it;DB_CLOSE_DELAY=-1;MODE=MariaDB",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false"
        }
)
class RejectionAlfrescoIT {

    @Autowired
    private AlfrescoDocumentService documentService;

    @Autowired
    private ManagerApprovalHandler managerApprovalHandler;

    @Autowired
    private TeleworkRequestRepository repository;

    @Value("${alfresco.rejected-folder-id}")
    private String rejectedFolderId;

    private String uploadedNodeId;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerRejectionMovesDocumentToRejectedFolder() throws Exception {
        authenticateAs("employee-1", "ROLE_EMPLOYEE");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "rejection-" + UUID.randomUUID() + ".txt",
                "text/plain",
                "reject-test".getBytes()
        );

        uploadedNodeId = documentService.upload(file);

        TeleworkRequest request = TeleworkRequest.create(
                "employee-1",
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 2)
        );
        request.attachJustificatif(uploadedNodeId);
        request.linkProcess("proc-1");
        request = repository.save(request);

        authenticateAs("manager-1", "ROLE_MANAGER");
        managerApprovalHandler.reject(
                request.getId(),
                "101",
                new ApprovalDecisionDTO(request.getId(), "manager-1", "Rejected")
        );

        String parentId = documentService.getParentId(uploadedNodeId);
        assertThat(parentId).isEqualTo(rejectedFolderId);
    }

    private void authenticateAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "n/a",
                        List.of(() -> role)
                )
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = "tn.pi.remoteflowapplication",
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = CamundaWorkflowService.class
            )
    )
    static class TestConfig {

        @Bean
        CamundaWorkflowService camundaWorkflowService() {
            return new NoopCamundaWorkflowService();
        }
    }

    static class NoopCamundaWorkflowService extends CamundaWorkflowService {
        NoopCamundaWorkflowService() {
            super(null);
        }

        @Override
        public String startTeleworkProcess(Long requestId, String employeeId, Boolean specialCase) {
            return "noop";
        }

        @Override
        public void completeTask(String taskKey, String decision, String comment, String assignee) {
            // no-op for integration test
        }
    }
}
