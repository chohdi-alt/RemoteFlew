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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.web.client.HttpStatusCodeException;

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

    private final RestTemplate restTemplate;

    private String ticket;

    public AlfrescoDocumentService(RestTemplate alfrescoRestTemplate) {
        this.restTemplate = alfrescoRestTemplate;
    }

    /*
     * ======================
     * AUTHENTICATION
     * ======================
     */

    private synchronized String getTicket() {
        if (ticket == null) {
            authenticate();
        }
        return ticket;
    }

    private void authenticate() {
        String url = baseUrl + "/api/-default-/public/authentication/versions/1/tickets";

        Map<String, String> body = Map.of(
                "userId", username,
                "password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<>() {
        };
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request, typeRef);

        Map<String, Object> bodyResponse = response.getBody();
        if (bodyResponse != null && bodyResponse.get("entry") instanceof Map<?, ?> entry) {
            ticket = (String) entry.get("id");
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, getTicket());
        return headers;
    }

    /*
     * ======================
     * GED OPERATIONS
     * ======================
     */

    @Override
    public String upload(MultipartFile file, String parentNodeId) throws IOException {
        String normalizedParentNodeId = normalizeNodeId(parentNodeId);
        String url = baseUrl + "/api/-default-/public/alfresco/versions/1/nodes/"
                + normalizedParentNodeId + "/children";

        final byte[] bytes = file.getBytes();
        final String filename = file.getOriginalFilename();

        return executeWithRetry(() -> {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(username, getTicket());
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request,
                    typeRef);

            Map<String, Object> bodyResponse = response.getBody();
            if (bodyResponse != null && bodyResponse.get("entry") instanceof Map<?, ?> entry) {
                return (String) entry.get("id"); // Alfresco nodeId
            }
            return null;
        });
    }

    @Override
    public String upload(MultipartFile file) throws IOException {
        return upload(file, pendingFolderId);
    }

    @Override
    public byte[] download(String nodeId) {
        String normalizedNodeId = normalizeNodeId(nodeId);
        String url = baseUrl + "/api/-default-/public/alfresco/versions/1/nodes/"
                + normalizedNodeId + "/content";

        return executeWithRetry(() -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(username, getTicket());
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, request, byte[].class);
            return response.getBody();
        });
    }

    public String getParentId(String nodeId) {
        String normalizedNodeId = normalizeNodeId(nodeId);
        String url = baseUrl + "/api/-default-/public/alfresco/versions/1/nodes/" + normalizedNodeId;

        return executeWithRetry(() -> {
            HttpEntity<Void> request = new HttpEntity<>(authHeaders());
            ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, request, typeRef);

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
        });
    }

    public void move(String nodeId, String targetFolderId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        String normalizedNodeId = normalizeNodeId(nodeId);
        String normalizedTargetFolderId = normalizeNodeId(targetFolderId);
        String url = baseUrl + "/api/-default-/public/alfresco/versions/1/nodes/" + normalizedNodeId;
        Map<String, String> body = Map.of("parentId", normalizedTargetFolderId);

        executeWithRetry(() -> {
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, authHeaders());
            return restTemplate.exchange(url, HttpMethod.PUT, request, Void.class);
        });
    }

    @Override
    public void moveToApproved(String nodeId) {
        move(nodeId, approvedFolderId);
    }

    @Override
    public void moveToRejected(String nodeId) {
        move(nodeId, rejectedFolderId);
    }

    /**
     * Fix Defect 4: Implement retry logic on 401 Unauthorized.
     * Safely refreshes the ticket and retries the operation once.
     */
    private <T> T executeWithRetry(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                synchronized (this) {
                    ticket = null; // Invalidate cached ticket
                    authenticate();
                }
                return operation.get(); // Retry once
            }
            throw ex;
        }
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
