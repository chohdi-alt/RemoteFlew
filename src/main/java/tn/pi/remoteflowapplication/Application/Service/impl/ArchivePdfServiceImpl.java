package tn.pi.remoteflowapplication.application.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import tn.pi.remoteflowapplication.application.service.ArchivePdfService;
import tn.pi.remoteflowapplication.domain.entity.TeleworkRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

@Service
public class ArchivePdfServiceImpl implements ArchivePdfService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public InputStream generateArchivePdf(TeleworkRequest request) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Telework Request Archive Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" ")); // Spacer

            // Section 1: Request Summary
            addSectionTitle(document, "Section 1: Request Summary");
            document.add(new Paragraph("Request ID: " + request.getId()));
            document.add(new Paragraph("Workflow Instance: " + (request.getProcessInstanceId() != null ? request.getProcessInstanceId() : "N/A")));
            document.add(new Paragraph("Status: " + request.getStatus().name()));
            document.add(new Paragraph("Date Submitted: " + (request.getSubmittedAt() != null ? DATE_FORMATTER.format(atZone(request.getSubmittedAt())) : "N/A")));
            document.add(new Paragraph(" "));

            // Section 2: Employee Info
            addSectionTitle(document, "Section 2: Employee Info");
            document.add(new Paragraph("Employee ID: " + request.getEmployeeId()));
            document.add(new Paragraph("Team ID: " + (request.getTeamId() != null ? request.getTeamId().toString() : "N/A")));
            document.add(new Paragraph("Telework Dates: From " + request.getStartDate() + " To " + request.getEndDate()));
            document.add(new Paragraph(" "));

            // Section 3: Decision Flow
            addSectionTitle(document, "Section 3: Decision Flow");
            document.add(new Paragraph("Manager Decision: By " + (request.getManagerExternalId() != null ? request.getManagerExternalId() : "N/A") + " on " + (request.getManagerDecisionAt() != null ? DATE_FORMATTER.format(atZone(request.getManagerDecisionAt())) : "N/A")));
            document.add(new Paragraph("Manager Comment: " + (request.getManagerComment() != null ? request.getManagerComment() : "N/A")));
            document.add(new Paragraph(" "));
            
            document.add(new Paragraph("HR Decision: By " + (request.getHrExternalId() != null ? request.getHrExternalId() : "N/A") + " on " + (request.getHrDecisionAt() != null ? DATE_FORMATTER.format(atZone(request.getHrDecisionAt())) : "N/A")));
            document.add(new Paragraph("HR Comment: " + (request.getHrComment() != null ? request.getHrComment() : "N/A")));
            document.add(new Paragraph(" "));

            // Section 4: Justification
            addSectionTitle(document, "Section 4: Justification");
            document.add(new Paragraph("Reason: " + (request.getJustificationReason() != null ? request.getJustificationReason() : "None provided")));
            document.add(new Paragraph(" "));

            // Section 5: Timeline (Audit Trail)
            addSectionTitle(document, "Section 5: Timeline (Audit Trail)");
            document.add(new Paragraph("Submitted: " + (request.getSubmittedAt() != null ? DATE_FORMATTER.format(atZone(request.getSubmittedAt())) : "N/A")));
            document.add(new Paragraph("Manager Reviewed: " + (request.getManagerDecisionAt() != null ? DATE_FORMATTER.format(atZone(request.getManagerDecisionAt())) : "N/A")));
            if (request.getApprovedAt() != null) {
                document.add(new Paragraph("Final Decision (Approved): " + DATE_FORMATTER.format(atZone(request.getApprovedAt()))));
            }
            if (request.getRejectedAt() != null) {
                document.add(new Paragraph("Final Decision (Rejected): " + DATE_FORMATTER.format(atZone(request.getRejectedAt()))));
            }
            document.add(new Paragraph(" "));

            document.close();
            return new ByteArrayInputStream(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Error generating archive PDF", e);
        }
    }

    private void addSectionTitle(Document document, String title) throws Exception {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Paragraph element = new Paragraph(title, font);
        element.setSpacingAfter(5f);
        document.add(element);
    }

    private java.time.ZonedDateTime atZone(java.time.Instant instant) {
        return instant.atZone(ZoneId.systemDefault());
    }
}
