package tn.pi.remoteflowapplication.application.port.out;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface DocumentStoragePort {
    String upload(MultipartFile file, String parentNodeId) throws IOException;

    String upload(MultipartFile file) throws IOException;

    byte[] download(String nodeId);

    void moveToApproved(String nodeId);

    void moveToRejected(String nodeId);

    String uploadFileFromStream(String filename, byte[] content, String parentNodeId) throws IOException;
}
