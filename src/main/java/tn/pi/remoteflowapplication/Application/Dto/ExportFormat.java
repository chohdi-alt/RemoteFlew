package tn.pi.remoteflowapplication.application.dto;

import tn.pi.remoteflowapplication.domain.exception.BusinessException;

import java.util.Locale;

public enum ExportFormat {
    PDF,
    CSV,
    XLSX;

    public static ExportFormat from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BusinessException("Export format is required (pdf, csv, xlsx)");
        }
        try {
            return ExportFormat.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Unsupported export format: " + rawValue);
        }
    }
}
