package tn.pi.remoteflowapplication.infrastructure.document;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DocumentMetadataMapper {
    public Map<String, String> toMetadata(String nodeId) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("alfrescoNodeId", nodeId);
        return metadata;
    }
}
