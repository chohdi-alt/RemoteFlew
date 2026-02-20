package tn.pi.remoteflowapplication.application.dto;

public record ExportedReportDTO(
        String fileName,
        String contentType,
        byte[] content
) {
}

