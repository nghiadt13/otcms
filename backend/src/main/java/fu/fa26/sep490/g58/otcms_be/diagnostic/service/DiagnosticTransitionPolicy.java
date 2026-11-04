package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticEvidenceContext;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;

/** Read-only transition policy contract for FEAT-003. */
public interface DiagnosticTransitionPolicy {
    DiagnosticRequestStatus targetStatus(DiagnosticAction action);

    DiagnosticOperationOutcome evaluate(DiagnosticRequest request, DiagnosticAction action,
            DiagnosticActor actor, DiagnosticEvidenceContext evidence, long expectedVersion);
}
