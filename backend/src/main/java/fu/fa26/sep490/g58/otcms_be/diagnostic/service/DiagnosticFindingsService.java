package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsReleaseView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticFindingsView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportUpload;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import java.util.UUID;

public interface DiagnosticFindingsService {
    DiagnosticRequestView review(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, String studyRef, String correlationId);

    DiagnosticFindingsView getCurrent(DiagnosticActor actor, UUID requestId);

    DiagnosticFindingsView saveDraft(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, String content, String correlationId);

    DiagnosticFindingsView createCorrection(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, String content, String correctionReason, String correlationId);

    DiagnosticReportView uploadReport(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, DiagnosticReportUpload upload,
            String correlationId);

    DiagnosticFindingsReleaseView complete(DiagnosticActor actor, UUID requestId, String idempotencyKey,
            long expectedVersion, int findingsVersion, UUID reportId, String studyRef, String correlationId);
}
