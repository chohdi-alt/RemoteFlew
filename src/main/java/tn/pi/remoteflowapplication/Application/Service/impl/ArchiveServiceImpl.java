package tn.pi.remoteflowapplication.application.service.impl;

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
        logger.info("[ARCHIVE_START] requestId={}", requestId);

        try {
            TeleworkRequest request = repository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("Request not found for id: " + requestId));

            if (request.getArchiveNodeId() != null && !request.getArchiveNodeId().isBlank()) {
                logger.warn("[ARCHIVE_SKIP] requestId={} already has archiveNodeId={} - skipping",
                        requestId, request.getArchiveNodeId());
                return;
            }

            try (InputStream pdfStream = pdfService.generateArchivePdf(request)) {
                byte[] content = pdfStream.readAllBytes();
                String filename = "Archive_Request_" + requestId + ".pdf";

                logger.info("[ARCHIVE_PDF_READY] requestId={} filename={} bytes={}",
                        requestId, filename, content.length);

                String nodeId = documentStoragePort.uploadFileFromStream(filename, content, archiveFolderId);
                if (nodeId == null || nodeId.isBlank()) {
                    throw new RuntimeException("Alfresco upload returned null nodeId for requestId=" + requestId);
                }

                String cleanNodeId = nodeId.replace("workspace://SpacesStore/", "");
                logger.info("[ARCHIVE_UPLOADED] requestId={} nodeId={}", requestId, cleanNodeId);

                int updated = repository.updateArchiveNodeId(requestId, cleanNodeId);
                if (updated == 0) {
                    throw new RuntimeException("ARCHIVE PERSIST FAILED for requestId=" + requestId);
                }

                logger.info("[ARCHIVE_DB_UPDATED] requestId={} archiveNodeId={} rows={}", requestId, cleanNodeId, updated);
            }
        } catch (Exception e) {
            logger.error("[ARCHIVE_FAILURE] requestId={} | Error: {}", requestId, e.getMessage(), e);
            throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
        }
    }
}
