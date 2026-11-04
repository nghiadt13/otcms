package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticEvidenceContext;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import java.util.UUID;

/** Authorization policy contract for FEAT-003. */
public interface DiagnosticAuthorizationPolicy {
    void requireAllowed(DiagnosticRequest request, DiagnosticAction action,
            DiagnosticActor actor, DiagnosticEvidenceContext evidence, long expectedVersion);

    DiagnosticOperationOutcome evaluateCreation(DiagnosticActor actor, UUID visitId, UUID patientId,
            VisitEvidence visitEvidence);

    void requireCreationAllowed(DiagnosticActor actor, UUID visitId, UUID patientId, VisitEvidence visitEvidence);
}
