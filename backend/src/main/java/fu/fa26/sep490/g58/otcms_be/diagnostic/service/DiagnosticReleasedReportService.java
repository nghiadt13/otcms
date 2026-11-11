package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Patient-authorized access to released PDF reports. */
public interface DiagnosticReleasedReportService {
    List<ReleasedReport> listForPatientVisit(DiagnosticActor actor, UUID visitId);
    ReportContent readForPatientVisit(DiagnosticActor actor, UUID visitId, UUID reportId);

    record ReleasedReport(UUID reportId, UUID visitId, int version, String status, String imagingType,
            String bodyArea, String studyRef, String findingsText, Instant releasedAt) {
        @Override public String toString() { return "ReleasedReport[redacted]"; }
    }
    record ReportContent(UUID reportId, int version, Instant releasedAt, byte[] bytes) {
        public ReportContent { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public String toString() { return "ReportContent[redacted]"; }
    }
}
