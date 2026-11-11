package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import java.util.UUID;

public record DiagnosticHistoryItem(UUID requestId, UUID visitId, String imagingType,
        DiagnosticRequestStatus status, String studyRef, Integer findingsVersion,
        String findingsText, UUID reportId, String bodyArea) {
    public DiagnosticHistoryItem(UUID requestId, UUID visitId, String imagingType,
            DiagnosticRequestStatus status, String studyRef, Integer findingsVersion,
            String findingsText, UUID reportId) {
        this(requestId, visitId, imagingType, status, studyRef, findingsVersion, findingsText, reportId, null);
    }
    @Override public String toString() { return "DiagnosticHistoryItem[redacted]"; }
}
