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

    InputStream openReleasedReport(UUID reportId, UUID actorId);
}
