package tn.pi.remoteflowapplication.application.service;

import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;

import java.io.InputStream;

public interface ArchivePdfService {
    InputStream generateArchivePdf(TeleworkRequest request);
}
