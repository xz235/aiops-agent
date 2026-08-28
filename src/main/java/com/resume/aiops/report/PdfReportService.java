package com.resume.aiops.report;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.resume.aiops.agent.domain.DiagnosisTask;
import com.resume.aiops.config.AiOpsProperties;
import com.resume.aiops.memory.domain.ToolCallRecord;
import com.resume.aiops.rag.RunbookEvidence;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
public class PdfReportService {
    private final AiOpsProperties properties;

    public PdfReportService(AiOpsProperties properties) {
        this.properties = properties;
    }

    public String generate(DiagnosisTask task, List<ToolCallRecord> calls,
                           List<RunbookEvidence> evidence, String conclusion) {
        String reportId = "report-" + task.taskId();
        try {
            Files.createDirectories(properties.reportsDirectory());
            Path target = properties.reportsDirectory().resolve(reportId + ".pdf").normalize();
            try (OutputStream output = Files.newOutputStream(target)) {
                Document document = new Document();
                PdfWriter.getInstance(document, output);
                document.open();
                document.add(new Paragraph("AIOps Diagnosis Report"));
                document.add(new Paragraph("Task: " + task.taskId()));
                document.add(new Paragraph("Service: " + task.alert().service()));
                document.add(new Paragraph("Severity: " + task.alert().severity()));
                document.add(new Paragraph("Conclusion: " + conclusion));
                document.add(new Paragraph("Tool evidence:"));
                for (ToolCallRecord call : calls) {
                    document.add(new Paragraph("- " + call.skillName() + " (" + call.latencyMs() + " ms): " + call.result()));
                }
                document.add(new Paragraph("Runbook references:"));
                for (RunbookEvidence item : evidence) {
                    document.add(new Paragraph("- " + item.title() + " [" + item.documentId() + "]"));
                }
                document.close();
            }
            return reportId;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot generate PDF report", exception);
        }
    }

    public Resource load(String reportId) {
        try {
            Path base = properties.reportsDirectory().toAbsolutePath().normalize();
            Path report = base.resolve(reportId + ".pdf").normalize();
            if (!report.startsWith(base) || !Files.exists(report)) {
                throw new NoSuchElementException("Report not found: " + reportId);
            }
            return new UrlResource(report.toUri());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load report", exception);
        }
    }
}

