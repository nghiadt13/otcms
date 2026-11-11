package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticEvidenceContext;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticTransitionPolicy;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Pure authorization and transition policy. It never mutates request or owner state. */
@Service
public final class DiagnosticTransitionPolicyImpl implements DiagnosticTransitionPolicy {
    @Override
    public DiagnosticRequestStatus targetStatus(DiagnosticAction action) {
        return switch (action) {
            case RECORD_FIRST_USE -> DiagnosticRequestStatus.IN_PROGRESS;
            case LINK_CONFIRMED_STUDY -> DiagnosticRequestStatus.RESULT_AVAILABLE;
            case REVIEW_RESULT -> DiagnosticRequestStatus.REVIEWED;
            case MANUAL_CANCEL, NO_SHOW_CANCEL -> DiagnosticRequestStatus.CANCELLED;
            case TRANSFER_ROOM -> DiagnosticRequestStatus.PENDING;
        };
    }

    @Override
    public DiagnosticOperationOutcome evaluate(DiagnosticRequest request, DiagnosticAction action,
            DiagnosticActor actor, DiagnosticEvidenceContext evidence, long expectedVersion) {
        if (request == null || action == null || actor == null || evidence == null) {
            return DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE;
        }
        if (!request.id().equals(evidence.requestId())
                || !request.visitId().equals(evidence.visitId())
                || !request.patientId().equals(evidence.patientId())) {
            return DiagnosticOperationOutcome.EVIDENCE_MISMATCH;
        }
        if (expectedVersion != request.version()) return DiagnosticOperationOutcome.VERSION_CONFLICT;

        return switch (action) {
            case RECORD_FIRST_USE -> evaluateFirstUse(request, actor, evidence);
            case LINK_CONFIRMED_STUDY -> evaluateStudyLink(request, actor, evidence);
            case REVIEW_RESULT -> request.status() != DiagnosticRequestStatus.RESULT_AVAILABLE
                    ? DiagnosticOperationOutcome.STATE_CONFLICT
                    : !isTreatingDoctor(request, actor, evidence)
                            ? DiagnosticOperationOutcome.UNAUTHORIZED_RECORD
                            : hasCurrentAttempt(request, evidence)
                                    ? DiagnosticOperationOutcome.ALLOWED
                                    : DiagnosticOperationOutcome.EVIDENCE_MISMATCH;
            case MANUAL_CANCEL -> request.status() != DiagnosticRequestStatus.PENDING
                    ? DiagnosticOperationOutcome.STATE_CONFLICT
                    : !isTreatingDoctor(request, actor, evidence)
                            ? DiagnosticOperationOutcome.UNAUTHORIZED_RECORD
                            : evidence.firstUseAlreadyRecorded()
                                    ? DiagnosticOperationOutcome.STATE_CONFLICT
                                    : DiagnosticOperationOutcome.ALLOWED;
            case TRANSFER_ROOM -> request.status() != DiagnosticRequestStatus.PENDING
                    ? DiagnosticOperationOutcome.STATE_CONFLICT
                    : !isTreatingDoctor(request, actor, evidence)
                            ? DiagnosticOperationOutcome.UNAUTHORIZED_RECORD
                            : DiagnosticOperationOutcome.ALLOWED;
            case NO_SHOW_CANCEL -> evaluateNoShowCancel(request, actor, evidence);
        };
    }

    private DiagnosticOperationOutcome evaluateFirstUse(DiagnosticRequest request, DiagnosticActor actor,
            DiagnosticEvidenceContext evidence) {
        if (request.status() != DiagnosticRequestStatus.PENDING) return DiagnosticOperationOutcome.STATE_CONFLICT;
        if (actor.kind() != DiagnosticActor.Kind.TRUSTED_INTEGRATION
                && !(actor.kind() == DiagnosticActor.Kind.USER && actor.role() == AccountRole.IMAGING_TECHNICIAN)) {
            return DiagnosticOperationOutcome.UNAUTHORIZED_RECORD;
        }
        return evidence.trustedFirstUse() && evidence.eventId() != null && evidence.attemptId() != null
                ? DiagnosticOperationOutcome.ALLOWED : DiagnosticOperationOutcome.EVIDENCE_MISMATCH;
    }

    private DiagnosticOperationOutcome evaluateStudyLink(DiagnosticRequest request, DiagnosticActor actor,
            DiagnosticEvidenceContext evidence) {
        if (request.status() != DiagnosticRequestStatus.IN_PROGRESS) return DiagnosticOperationOutcome.STATE_CONFLICT;
        if (actor.kind() != DiagnosticActor.Kind.TRUSTED_INTEGRATION) {
            return DiagnosticOperationOutcome.UNAUTHORIZED_RECORD;
        }
        if (!evidence.confirmedStudyMatch() || evidence.eventId() == null || request.currentAttempt() == null
                || evidence.attemptId() == null || !request.currentAttempt().id().equals(evidence.attemptId())) {
            return DiagnosticOperationOutcome.EVIDENCE_MISMATCH;
        }
        return DiagnosticOperationOutcome.ALLOWED;
    }

    private DiagnosticOperationOutcome evaluateNoShowCancel(DiagnosticRequest request, DiagnosticActor actor,
            DiagnosticEvidenceContext evidence) {
        if (request.status() != DiagnosticRequestStatus.PENDING) return DiagnosticOperationOutcome.STATE_CONFLICT;
        if (actor.kind() != DiagnosticActor.Kind.SYSTEM) return DiagnosticOperationOutcome.UNAUTHORIZED_RECORD;
        return evidence.noShowGateAccepted() && !evidence.visitResumed() && !evidence.firstUseAlreadyRecorded()
                ? DiagnosticOperationOutcome.ALLOWED : DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE;
    }

    private static boolean isTreatingDoctor(DiagnosticRequest request, DiagnosticActor actor,
            DiagnosticEvidenceContext evidence) {
        return actor.kind() == DiagnosticActor.Kind.USER
                && actor.role() == AccountRole.DOCTOR
                && evidence.treatingDoctorId() != null
                && actor.accountId().equals(evidence.treatingDoctorId())
                && request.orderingDoctorId().equals(evidence.treatingDoctorId());
    }

    private static boolean hasCurrentAttempt(DiagnosticRequest request, DiagnosticEvidenceContext evidence) {
        return request.currentAttempt() != null && evidence.attemptId() != null
                && Objects.equals(request.currentAttempt().id(), evidence.attemptId());
    }
}
