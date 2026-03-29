package tn.pi.remoteflowapplication.application.service;

import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;

public interface ArchiveService {
    void processArchive(Long requestId);
}
