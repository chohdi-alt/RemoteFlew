package tn.pi.remoteflowapplication.tests.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tn.pi.remoteflowapplication.config.AlfrescoConfig;
import tn.pi.remoteflowapplication.infrastructure.document.AlfrescoDocumentService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {AlfrescoConfig.class, AlfrescoDocumentService.class})
class AlfrescoDocumentIntegrationIT {

    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void registerAlfrescoProperties(DynamicPropertyRegistry registry) {
        registry.add("alfresco.base-url", () -> "http://localhost:" + WIREMOCK.port() + "/alfresco");
        registry.add("alfresco.username", () -> "alfresco-user");
        registry.add("alfresco.password", () -> "secret");
        registry.add("alfresco.pending-folder-id", () -> "workspace://SpacesStore/pending-folder");
        registry.add("alfresco.approved-folder-id", () -> "workspace://SpacesStore/approved-folder");
        registry.add("alfresco.rejected-folder-id", () -> "workspace://SpacesStore/rejected-folder");
        registry.add("alfresco.archive-folder-id", () -> "workspace://SpacesStore/archive-folder");
    }

    @Autowired
    private AlfrescoDocumentService alfrescoDocumentService;

    @BeforeEach
    void resetWireMock() {
        WIREMOCK.resetAll();
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @Test
    void shouldUploadAndDownloadDocumentThroughAlfrescoApi() throws Exception {
        WIREMOCK.stubFor(post(urlEqualTo("/alfresco/api/-default-/public/alfresco/versions/1/nodes/pending-folder/children"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"entry\":{\"id\":\"node-123\"}}")));

        byte[] storedContent = "%PDF-1.4 test-content".getBytes(StandardCharsets.UTF_8);
        WIREMOCK.stubFor(get(urlEqualTo("/alfresco/api/-default-/public/alfresco/versions/1/nodes/node-123/content"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                        .withBody(storedContent)));

        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "justification.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                storedContent);

        String nodeId = alfrescoDocumentService.upload(upload, "workspace://SpacesStore/pending-folder");
        assertEquals("node-123", nodeId);

        byte[] downloaded = alfrescoDocumentService.download("workspace://SpacesStore/node-123");
        assertArrayEquals(storedContent, downloaded);

        String expectedBasicAuth = "Basic " + Base64.getEncoder().encodeToString("alfresco-user:secret".getBytes(StandardCharsets.UTF_8));
        WIREMOCK.verify(postRequestedFor(urlEqualTo("/alfresco/api/-default-/public/alfresco/versions/1/nodes/pending-folder/children"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(expectedBasicAuth)));
        WIREMOCK.verify(getRequestedFor(urlEqualTo("/alfresco/api/-default-/public/alfresco/versions/1/nodes/node-123/content"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo(expectedBasicAuth)));
    }
}
