package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StoredReport;
import java.io.InputStream;
import java.util.UUID;

/** Private report bytes are held outside PostgreSQL and are never served before release. */
public interface ReportStoragePort {
    StoredReport storePrivateReport(UUID requestId, UUID visitId, String studyRef,
            int reportVersion, InputStream content, long contentLength);

    ReportValidationStatus validateStoredReport(UUID reportId);

    /** Adapter-specific reference for object keys that carry visit and version context. */
    default ReportValidationStatus validateStoredReport(UUID reportId, String storageRef) {
        return validateStoredReport(reportId);
    }

    InputStream openReleasedReport(UUID reportId, UUID actorId);

    default InputStream openReleasedReport(UUID reportId, String storageRef, UUID actorId) {
        return openReleasedReport(reportId, actorId);
    }
}
