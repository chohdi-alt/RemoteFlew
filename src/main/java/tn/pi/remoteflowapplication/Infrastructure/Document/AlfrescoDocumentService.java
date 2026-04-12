package tn.pi.remoteflowapplication.infrastructure.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.web.client.HttpStatusCodeException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AlfrescoDocumentService implements DocumentStoragePort {
    private static final String WORKSPACE_NODE_REF_PREFIX = "workspace://SpacesStore/";

    @Value("${alfresco.base-url}")
    private String baseUrl;

    @Value("${alfresco.username}")
    private String username;

    @Value("${alfresco.password}")
    private String password;

    @Value("${alfresco.pending-folder-id}")
    private String pendingFolderId;

    @Value("${alfresco.approved-folder-id}")
    private String approvedFolderId;

    @Value("${alfresco.rejected-folder-id}")
    private String rejectedFolderId;

    @Value("${alfresco.archive-folder-id}")
    private String archiveFolderId;

    private final RestTemplate restTemplate;

    public AlfrescoDocumentService(RestTemplate alfrescoRestTemplate) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        alfrescoRestTemplate.setRequestFactory(factory);
        this.restTemplate = alfrescoRestTemplate;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        return headers;
    }

    /*
     * ======================
     * GED OPERATIONS
     * ======================
     */

    @Override
    public String upload(MultipartFile file, String parentNodeId) throws IOException {
        String filename = file.getOriginalFilename();

        // Step 2 — Backend Extension Validation
        List<String> allowedExtensions = List.of("pdf", "png", "jpg", "jpeg");
        String ext = FilenameUtils.getExtension(filename).toLowerCase();
        if (!allowedExtensions.contains(ext)) {
            throw new IllegalArgumentException("Invalid file type");
        }

        // Step 3 — MIME Type Validation
        String mimeType = file.getContentType();
        List<String> allowedMime = List.of("application/pdf", "image/png", "image/jpeg");
        if (!allowedMime.contains(mimeType)) {
            throw new IllegalArgumentException("Invalid MIME type");
        }

        // Step 4 — Magic Byte Validation
        Tika tika = new Tika();
        String detectedType = tika.detect(file.getInputStream());
        if (!allowedMime.contains(detectedType)) {
            throw new IllegalArgumentException("Invalid file content");
        }

        // Step 5 — Safe Filename Handling
        String safeFilename = Paths.get(filename).getFileName().toString();
        safeFilename = safeFilename.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
        final String finalSafeFilename = safeFilename;

        String normalizedParentNodeId = normalizeNodeId(parentNodeId);
        String url = baseUrl + "/api/-default-/public/alfresco/versions/1/nodes/"
                + normalizedParentNodeId + "/children";

        final byte[] bytes = file.getBytes();

        log.info("Calling Alfresco [UPLOAD]: {} | Method: POST", url);
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("filedata", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return finalSafeFilename;
                }
            });

            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request,
                    typeRef);

            log.info("Alfresco response status: {}", response.getStatusCode());
            Map<String, Object> bodyResponse = response.getBody();
            if (bodyResponse != null && bodyResponse.get("entry") instanceof Map<?, ?> entry) {
                return (String) entry.get("id"); // Alfresco nodeId
            }
            return null;
        } catch (HttpStatusCodeException e) {
            log.error("Alfresco error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Alfresco error during upload to {}", url, e);
            return null;
        }
    }

    @Override
    public String upload(MultipartFile file) throws IOException {
        return upload(file, pendingFolderId);
    }

    @Override
    public String uploadFileFromStream(String filename, byte[] content, String parentNodeId) throws IOException {
        String normalizedParentNodeId = normalizeNodeId(parentNodeId);
        String url = baseUrl + "/api/-default-/public/alfresco/versions/1/nodes/"
                + normalizedParentNodeId + "/children";

        log.info("Calling Alfresco [UPLOAD_STREAM]: {} | Method: POST", url);
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("filedata", new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request,
                    typeRef);

            log.info("Alfresco response status: {}", response.getStatusCode());
            Map<String, Object> bodyResponse = response.getBody();
            if (bodyResponse != null && bodyResponse.get("entry") instanceof Map<?, ?> entry) {
                return (String) entry.get("id"); // Alfresco nodeId
            }
            return null;
        } catch (HttpStatusCodeException e) {
            log.error("Alfresco error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Alfresco error during stream upload to {}", url, e);
            return null;
        }
    }

    @Override
    public byte[] download(String nodeId) {
        String normalizedNodeId = normalizeNodeId(nodeId);
        String url = baseUrl + "/api/-default-/public/alfresco/versions/1/nodes/"
                + normalizedNodeId + "/content";

        log.info("[ARCHIVE_DOWNLOAD] nodeId={} url={}", normalizedNodeId, url);
        try {
            HttpHeaders headers = authHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, request, byte[].class);
            log.info("[ALFRESCO_RESPONSE] status={} nodeId={}", response.getStatusCode(), normalizedNodeId);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("[ALFRESCO_RESPONSE] status={} nodeId={} body={}", e.getStatusCode(), normalizedNodeId, e.getResponseBodyAsString());
            throw new tn.pi.remoteflowapplication.domain.exception.BusinessException("Document unavailable");
        } catch (Exception e) {
            log.error("Alfresco error during download from {}", url, e);
            throw new tn.pi.remoteflowapplication.domain.exception.BusinessException("Document unavailable");
        }
    }

    public String getParentId(String nodeId) {
        String normalizedNodeId = normalizeNodeId(nodeId);
        String url = baseUrl + "/api/-default-/public/alfresco/versions/1/nodes/" + normalizedNodeId;

        log.info("Calling Alfresco [GET_PARENT]: {} | Method: GET", url);
        try {
            HttpEntity<Void> request = new HttpEntity<>(authHeaders());
            ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, request, typeRef);

            log.info("Alfresco response status: {}", response.getStatusCode());
            Map<String, Object> bodyResponse = response.getBody();
            if (bodyResponse != null && bodyResponse.get("entry") instanceof Map<?, ?> entry) {
                Object parentId = entry.get("parentId");
                if (parentId != null) {
                    return toNodeRef(parentId.toString());
                }

                if (entry.get("path") instanceof Map<?, ?> path) {
                    if (path.get("elements") instanceof List<?> elements) {
                        if (!elements.isEmpty()) {
                            if (elements.get(elements.size() - 1) instanceof Map<?, ?> last) {
                                Object pathId = last.get("id");
                                if (pathId != null) {
                                    return toNodeRef(pathId.toString());
                                }
                            }
                        }
                    }
                }
            }
            return null;
        } catch (HttpStatusCodeException e) {
            log.error("Alfresco error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Alfresco error during getParentId for {}", url, e);
            return null;
        }
    }

    public void move(String nodeId, String targetFolderId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        String normalizedNodeId = normalizeNodeId(nodeId);
        String normalizedTargetFolderId = normalizeNodeId(targetFolderId);
        String url = baseUrl + "/api/-default-/public/alfresco/versions/1/nodes/" + normalizedNodeId + "/move";
        Map<String, String> body = Map.of("targetParentId", normalizedTargetFolderId);

        log.info("Calling Alfresco [MOVE]: {} | Method: POST", url);
        try {
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, authHeaders());
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            log.info("Alfresco response status: {}", response.getStatusCode());
        } catch (HttpStatusCodeException e) {
            log.error("Alfresco error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Alfresco error during move for {}", url, e);
        }
    }

    @Override
    public void moveToApproved(String nodeId) {
        move(nodeId, approvedFolderId);
    }

    @Override
    public void moveToRejected(String nodeId) {
        move(nodeId, rejectedFolderId);
    }


    private String normalizeNodeId(String nodeIdOrNodeRef) {
        if (nodeIdOrNodeRef == null) {
            return null;
        }
        String value = nodeIdOrNodeRef.trim();
        if (value.startsWith(WORKSPACE_NODE_REF_PREFIX)) {
            return value.substring(WORKSPACE_NODE_REF_PREFIX.length());
        }
        if (value.contains("://") && value.contains("/")) {
            return value.substring(value.lastIndexOf('/') + 1);
        }
        return value;
    }

    private String toNodeRef(String nodeIdOrNodeRef) {
        if (nodeIdOrNodeRef == null) {
            return null;
        }
        String value = nodeIdOrNodeRef.trim();
        if (value.startsWith("workspace://")) {
            return value;
        }
        return WORKSPACE_NODE_REF_PREFIX + value;
    }
}
