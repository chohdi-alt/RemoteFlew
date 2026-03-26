package tn.pi.remoteflowapplication.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(properties = {
    "spring.config.location=file:src/main/resources/application.properties",
    "spring.main.allow-bean-definition-overriding=true"
})
@EnabledIfEnvironmentVariable(named = "ALFRESCO_IT", matches = "true")
class AlfrescoDocumentServiceIT {

    @Autowired
    private AlfrescoDocumentService documentService;

    @Value("${alfresco.pending-folder-id}")
    private String pendingFolderId;

    @Value("${alfresco.approved-folder-id}")
    private String approvedFolderId;

    @Value("${alfresco.rejected-folder-id}")
    private String rejectedFolderId;

    @Test
    void upload_shouldStoreFileInPendingFolder() throws Exception {
        MockMultipartFile file = createTestFile("pending");

        String nodeId = documentService.upload(file);

        assertThat(nodeId).isNotBlank();
        assertThat(documentService.getParentId(nodeId)).isEqualTo(pendingFolderId);

        documentService.moveToRejected(nodeId);
    }

    @Test
    void approve_shouldMoveFileToApprovedFolder() throws Exception {
        MockMultipartFile file = createTestFile("approved");
        String nodeId = documentService.upload(file);

        assertThatCode(() -> documentService.moveToApproved(nodeId))
                .doesNotThrowAnyException();
        assertThat(documentService.getParentId(nodeId)).isEqualTo(approvedFolderId);
    }

    @Test
    void reject_shouldMoveFileToRejectedFolder() throws Exception {
        MockMultipartFile file = createTestFile("rejected");
        String nodeId = documentService.upload(file);

        assertThatCode(() -> documentService.moveToRejected(nodeId))
                .doesNotThrowAnyException();
        assertThat(documentService.getParentId(nodeId)).isEqualTo(rejectedFolderId);
    }

    private MockMultipartFile createTestFile(String label) {
        String filename = "it-" + label + "-" + UUID.randomUUID() + ".txt";
        return new MockMultipartFile(
                "file",
                filename,
                "text/plain",
                ("test-" + label).getBytes(StandardCharsets.UTF_8)
        );
    }
}
