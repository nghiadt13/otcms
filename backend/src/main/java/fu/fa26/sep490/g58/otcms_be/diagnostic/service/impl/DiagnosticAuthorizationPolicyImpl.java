package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticEvidenceContext;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticAuthorizationPolicy;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticTransitionPolicy;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class DiagnosticAuthorizationPolicyImpl implements DiagnosticAuthorizationPolicy {
    private final DiagnosticTransitionPolicy transitionPolicy;

    public DiagnosticAuthorizationPolicyImpl(DiagnosticTransitionPolicy transitionPolicy) {
        this.transitionPolicy = Objects.requireNonNull(transitionPolicy, "transitionPolicy");
    }

    @Override
    public void requireAllowed(DiagnosticRequest request, DiagnosticAction action,
            DiagnosticActor actor, DiagnosticEvidenceContext evidence, long expectedVersion) {
        DiagnosticOperationOutcome outcome = transitionPolicy.evaluate(
                request, action, actor, evidence, expectedVersion);
        if (outcome != DiagnosticOperationOutcome.ALLOWED) {
            throw new DiagnosticOperationException(outcome);
        }
    }

    @Override
    public DiagnosticOperationOutcome evaluateCreation(DiagnosticActor actor, UUID visitId, UUID patientId,
            VisitEvidence visitEvidence) {
        if (actor == null || visitId == null || patientId == null || visitEvidence == null) {
            return DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE;
        }
        if (!Objects.equals(visitId, visitEvidence.visitId())
                || !Objects.equals(patientId, visitEvidence.patientId())) {
            return DiagnosticOperationOutcome.EVIDENCE_MISMATCH;
        }
        if (actor.kind() != DiagnosticActor.Kind.USER || actor.role() != AccountRole.DOCTOR
                || visitEvidence.treatingDoctorId() == null
                || !actor.accountId().equals(visitEvidence.treatingDoctorId())) {
            return DiagnosticOperationOutcome.UNAUTHORIZED_RECORD;
        }
        return visitEvidence.open() && visitEvidence.examinationStarted() && visitEvidence.eligible()
                ? DiagnosticOperationOutcome.ALLOWED : DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE;
    }

    @Override
    public void requireCreationAllowed(DiagnosticActor actor, UUID visitId, UUID patientId,
            VisitEvidence visitEvidence) {
        DiagnosticOperationOutcome outcome = evaluateCreation(actor, visitId, patientId, visitEvidence);
        if (outcome != DiagnosticOperationOutcome.ALLOWED) {
            throw new DiagnosticOperationException(outcome);
        }
    }
}
