package tn.pi.remoteflowapplication.application.service.impl;

import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.pi.remoteflowapplication.application.port.out.DocumentStoragePort;
import tn.pi.remoteflowapplication.application.port.out.TeleworkRequestRepository;
import tn.pi.remoteflowapplication.application.service.ArchivePdfService;
import tn.pi.remoteflowapplication.application.service.ArchiveService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.io.InputStream;

@Service
public class ArchiveServiceImpl implements ArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveServiceImpl.class);

    private final ArchivePdfService pdfService;
    private final DocumentStoragePort documentStoragePort;
    private final TeleworkRequestRepository repository;

    @Value("${alfresco.archive-folder-id}")
    private String archiveFolderId;

    public ArchiveServiceImpl(ArchivePdfService pdfService,
            DocumentStoragePort documentStoragePort,
            TeleworkRequestRepository repository) {
        this.pdfService = pdfService;
        this.documentStoragePort = documentStoragePort;
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processArchive(Long requestId) {
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : "SYSTEM";
        String user = MDC.get("user") != null ? MDC.get("user") : "system";
        String ip = MDC.get("ip");
        if (ip == null || ip.isBlank())
            ip = "unknown";

        logger.info("FILE_EVENT",
                kv("event", "ARCHIVE_START"),
                kv("event_normalized", "file.archive.started"),
                kv("category", "FILE"),
                kv("outcome", "ATTEMPT"),
                kv("severity", "LOW"),
                kv("requestId", requestId),
                kv("user", user),
                kv("ip", ip),
                kv("ip_private", isPrivateIp(ip)),
                kv("traceId", traceId),
                kv("connection_type", "HTTP"),
                kv("layer", "APPLICATION"),
                kv("source", "remoteflow-backend"));

        try {
            TeleworkRequest request = repository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("Request not found for id: " + requestId));

            if (request.getArchiveNodeId() != null && !request.getArchiveNodeId().isBlank()) {
                logger.warn("FILE_EVENT",
                        kv("event", "ARCHIVE_SKIP"),
                        kv("event_normalized", "file.archive.skipped"),
                        kv("category", "FILE"),
                        kv("outcome", "SUCCESS"),
                        kv("severity", "LOW"),
                        kv("requestId", requestId),
                        kv("archiveNodeId", request.getArchiveNodeId()),
                        kv("reason", "already_archived"),
                        kv("user", user),
                        kv("ip", ip),
                        kv("ip_private", isPrivateIp(ip)),
                        kv("traceId", traceId),
                        kv("connection_type", "HTTP"),
                        kv("layer", "APPLICATION"),
                        kv("source", "remoteflow-backend"));
                return;
            }

            try (InputStream pdfStream = pdfService.generateArchivePdf(request)) {
                byte[] content = pdfStream.readAllBytes();
                String filename = "Archive_Request_" + requestId + ".pdf";

                logger.info("FILE_EVENT",
                        kv("event", "ARCHIVE_PDF_READY"),
                        kv("event_normalized", "file.archive.pdf_ready"),
                        kv("category", "FILE"),
                        kv("outcome", "SUCCESS"),
                        kv("severity", "LOW"),
                        kv("requestId", requestId),
                        kv("filename", filename),
                        kv("contentLength", content.length),
                        kv("user", user),
                        kv("ip", ip),
                        kv("ip_private", isPrivateIp(ip)),
                        kv("traceId", traceId),
                        kv("connection_type", "HTTP"),
                        kv("layer", "APPLICATION"),
                        kv("source", "remoteflow-backend"));

                String nodeId = documentStoragePort.uploadFileFromStream(filename, content, archiveFolderId);
                if (nodeId == null || nodeId.isBlank()) {
                    throw new RuntimeException("Alfresco upload returned null nodeId for requestId=" + requestId);
                }

                String cleanNodeId = nodeId.replace("workspace://SpacesStore/", "");
                logger.info("FILE_EVENT",
                        kv("event", "ARCHIVE_UPLOADED"),
                        kv("event_normalized", "file.archive.uploaded"),
                        kv("category", "FILE"),
                        kv("outcome", "SUCCESS"),
                        kv("severity", "LOW"),
                        kv("requestId", requestId),
                        kv("nodeId", cleanNodeId),
                        kv("user", user),
                        kv("ip", ip),
                        kv("ip_private", isPrivateIp(ip)),
                        kv("traceId", traceId),
                        kv("connection_type", "HTTP"),
                        kv("layer", "APPLICATION"),
                        kv("source", "remoteflow-backend"));

                int updated = repository.updateArchiveNodeId(requestId, cleanNodeId);
                if (updated == 0) {
                    throw new RuntimeException("ARCHIVE PERSIST FAILED for requestId=" + requestId);
                }

                logger.info("FILE_EVENT",
                        kv("event", "ARCHIVE_COMPLETED"),
                        kv("event_normalized", "file.archive.completed"),
                        kv("category", "FILE"),
                        kv("outcome", "SUCCESS"),
                        kv("severity", "HIGH"),
                        kv("requestId", requestId),
                        kv("archiveNodeId", cleanNodeId),
                        kv("user", user),
                        kv("ip", ip),
                        kv("ip_private", isPrivateIp(ip)),
                        kv("traceId", traceId),
                        kv("connection_type", "HTTP"),
                        kv("layer", "APPLICATION"),
                        kv("source", "remoteflow-backend"));
            }
        } catch (Exception e) {
            String traceId1 = MDC.get("traceId") != null ? MDC.get("traceId") : "SYSTEM";
            String ipErr = MDC.get("ip");
            if (ipErr == null || ipErr.isBlank())
                ipErr = "unknown";

            logger.error("FILE_EVENT",
                    kv("event", "ARCHIVE_FAILURE"),
                    kv("event_normalized", "file.archive.failed"),
                    kv("category", "FILE"),
                    kv("outcome", "FAILURE"),
                    kv("severity", "HIGH"),
                    kv("requestId", requestId),
                    kv("user", user),
                    kv("ip", ipErr),
                    kv("ip_private", isPrivateIp(ipErr)),
                    kv("traceId", traceId1),
                    kv("error", e.getClass().getSimpleName()),
                    kv("error_message", e.getMessage()),
                    kv("connection_type", "HTTP"),
                    kv("layer", "APPLICATION"),
                    kv("source", "remoteflow-backend"));
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    private boolean isPrivateIp(String ip) {
        if (ip == null || "unknown".equalsIgnoreCase(ip))
            return false;
        return ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }
}
