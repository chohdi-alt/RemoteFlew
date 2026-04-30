package tn.pi.remoteflowapplication.tests.unit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.tests.integration.BaseIntegrationIT;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@Tag("integration")
class AlfrescoDocumentIntegrationIT extends BaseIntegrationIT {

    // ✅ use existing mock from BaseIntegrationIT
    @Autowired
    private DocumentStoragePort documentStoragePort;

    @Test
    void shouldUploadAndDownloadDocumentThroughAlfrescoApi() throws Exception {
        byte[] storedContent = "%PDF-1.4 test-content".getBytes(StandardCharsets.UTF_8);

        when(documentStoragePort.upload(any(), eq("workspace://SpacesStore/pending-folder")))
                .thenReturn("node-123");

        when(documentStoragePort.download(eq("workspace://SpacesStore/node-123")))
                .thenReturn(storedContent);

        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "justification.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                storedContent);

        String nodeId = documentStoragePort.upload(upload, "workspace://SpacesStore/pending-folder");
        assertEquals("node-123", nodeId);

        byte[] downloaded = documentStoragePort.download("workspace://SpacesStore/node-123");
        assertArrayEquals(storedContent, downloaded);
    }
}