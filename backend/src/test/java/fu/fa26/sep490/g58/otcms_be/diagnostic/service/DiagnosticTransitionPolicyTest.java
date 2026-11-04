package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAttempt;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticEvidenceContext;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticIdempotencyIdentity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticAction;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticAuthorizationPolicyImpl;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticTransitionPolicyImpl;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiagnosticTransitionPolicyTest {
    private final UUID requestId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-04T08:00:00Z");
    private DiagnosticTransitionPolicy policy;
    private DiagnosticEvidenceContext evidence;

    @BeforeEach
    void setUp() {
        policy = new DiagnosticTransitionPolicyImpl();
        evidence = evidence(true, true, true, false, false);
    }

    @Test
    void permitsOnlyContractedTransitionsForAuthorizedActors() {
        assertAllowed(request(DiagnosticRequestStatus.PENDING, null), DiagnosticAction.RECORD_FIRST_USE,
                DiagnosticActor.trustedIntegration(), evidence);
        assertAllowed(request(DiagnosticRequestStatus.IN_PROGRESS, attempt()), DiagnosticAction.LINK_CONFIRMED_STUDY,
                DiagnosticActor.trustedIntegration(), evidence);
        assertAllowed(request(DiagnosticRequestStatus.RESULT_AVAILABLE, attempt()), DiagnosticAction.REVIEW_RESULT,
                DiagnosticActor.user(doctorId, AccountRole.DOCTOR), evidence);
        assertAllowed(request(DiagnosticRequestStatus.PENDING, null), DiagnosticAction.MANUAL_CANCEL,
                DiagnosticActor.user(doctorId, AccountRole.DOCTOR), evidence);
        assertAllowed(request(DiagnosticRequestStatus.PENDING, null), DiagnosticAction.TRANSFER_ROOM,
                DiagnosticActor.user(doctorId, AccountRole.DOCTOR), evidence);
        assertAllowed(request(DiagnosticRequestStatus.PENDING, null), DiagnosticAction.NO_SHOW_CANCEL,
                DiagnosticActor.system(), evidence);
    }

    @Test
    void rejectsEveryActionFromTerminalOrIncompatibleStates() {
        for (DiagnosticRequestStatus status : DiagnosticRequestStatus.values()) {
            for (DiagnosticAction action : DiagnosticAction.values()) {
                boolean allowed = switch (action) {
                    case RECORD_FIRST_USE, MANUAL_CANCEL, TRANSFER_ROOM, NO_SHOW_CANCEL ->
                            status == DiagnosticRequestStatus.PENDING;
                    case LINK_CONFIRMED_STUDY -> status == DiagnosticRequestStatus.IN_PROGRESS;
                    case REVIEW_RESULT -> status == DiagnosticRequestStatus.RESULT_AVAILABLE;
                };
                if (!allowed) {
                    DiagnosticRequest request = request(status, requiresAttempt(status) ? attempt() : null);
                    DiagnosticActor actor = actorFor(action);
                    assertEquals(DiagnosticOperationOutcome.STATE_CONFLICT,
                            policy.evaluate(request, action, actor, evidence, request.version()),
                            status + " must reject " + action);
                    assertEquals(status, request.status(), "policy must not mutate rejected request");
                    assertEquals(0, request.version(), "policy must not advance a rejected request version");
                }
            }
        }
    }

    @Test
    void rejectsTechnicianAndWrongDoctorWithoutMutatingRequest() {
        DiagnosticRequest request = request(DiagnosticRequestStatus.PENDING, null);
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD,
                policy.evaluate(request, DiagnosticAction.MANUAL_CANCEL,
                        DiagnosticActor.user(UUID.randomUUID(), AccountRole.IMAGING_TECHNICIAN), evidence,
                        request.version()));
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD,
                policy.evaluate(request, DiagnosticAction.MANUAL_CANCEL,
                        DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR), evidence, request.version()));
        assertEquals(DiagnosticRequestStatus.PENDING, request.status());
        assertEquals(0, request.version());
    }

    @Test
    void rejectsMismatchedOwnerEvidenceAndStaleVersion() {
        DiagnosticRequest request = request(DiagnosticRequestStatus.PENDING, null);
        DiagnosticEvidenceContext[] mismatches = {
            new DiagnosticEvidenceContext(UUID.randomUUID(), visitId, patientId, doctorId, UUID.randomUUID(),
                    attemptId, true, true, true, false, false),
            new DiagnosticEvidenceContext(requestId, UUID.randomUUID(), patientId, doctorId, UUID.randomUUID(),
                    attemptId, true, true, true, false, false),
            new DiagnosticEvidenceContext(requestId, visitId, UUID.randomUUID(), doctorId, UUID.randomUUID(),
                    attemptId, true, true, true, false, false)
        };
        for (DiagnosticEvidenceContext mismatch : mismatches) {
            assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH,
                    policy.evaluate(request, DiagnosticAction.RECORD_FIRST_USE,
                            DiagnosticActor.trustedIntegration(), mismatch, request.version()));
        }
        assertEquals(DiagnosticRequestStatus.PENDING, request.status());
        assertEquals(0, request.version());
        assertEquals(DiagnosticOperationOutcome.VERSION_CONFLICT,
                policy.evaluate(request, DiagnosticAction.RECORD_FIRST_USE,
                        DiagnosticActor.trustedIntegration(), evidence, request.version() + 1));
    }

    @Test
    void requiresTrustedFirstUseAndConfirmedStudyEvidence() {
        DiagnosticRequest pending = request(DiagnosticRequestStatus.PENDING, null);
        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH,
                policy.evaluate(pending, DiagnosticAction.RECORD_FIRST_USE, DiagnosticActor.trustedIntegration(),
                        evidence(false, true, true, false, false), pending.version()));
        DiagnosticRequest started = request(DiagnosticRequestStatus.IN_PROGRESS, attempt());
        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH,
                policy.evaluate(started, DiagnosticAction.LINK_CONFIRMED_STUDY,
                        DiagnosticActor.trustedIntegration(), evidence(true, false, true, false, false),
                        started.version()));
    }

    @Test
    void noShowRequiresAcceptedGateAndNoResumeOrFirstUse() {
        DiagnosticRequest request = request(DiagnosticRequestStatus.PENDING, null);
        assertEquals(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE,
                policy.evaluate(request, DiagnosticAction.NO_SHOW_CANCEL, DiagnosticActor.system(),
                        evidence(true, true, false, false, false), request.version()));
        assertEquals(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE,
                policy.evaluate(request, DiagnosticAction.NO_SHOW_CANCEL, DiagnosticActor.system(),
                        evidence(true, true, true, true, false), request.version()));
        assertEquals(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE,
                policy.evaluate(request, DiagnosticAction.NO_SHOW_CANCEL, DiagnosticActor.system(),
                        evidence(true, true, true, false, true), request.version()));
    }

    @Test
    void rejectsStudyEvidenceForAnotherAttempt() {
        DiagnosticRequest request = request(DiagnosticRequestStatus.IN_PROGRESS, attempt());
        DiagnosticEvidenceContext anotherAttempt = new DiagnosticEvidenceContext(requestId, visitId, patientId,
                doctorId, UUID.randomUUID(), UUID.randomUUID(), true, true, true, false, false);
        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH,
                policy.evaluate(request, DiagnosticAction.LINK_CONFIRMED_STUDY,
                        DiagnosticActor.trustedIntegration(), anotherAttempt, request.version()));
    }

    @Test
    void idempotencyReplaysSamePayloadAndRejectsChangedPayload() {
        DiagnosticIdempotencyIdentity stored = new DiagnosticIdempotencyIdentity(
                doctorId, null, visitId, null, DiagnosticAction.MANUAL_CANCEL, "request-key", "payload-a");
        DiagnosticIdempotencyIdentity same = new DiagnosticIdempotencyIdentity(
                doctorId, null, visitId, null, DiagnosticAction.MANUAL_CANCEL, "request-key", "payload-a");
        DiagnosticIdempotencyIdentity changed = new DiagnosticIdempotencyIdentity(
                doctorId, null, visitId, null, DiagnosticAction.MANUAL_CANCEL, "request-key", "payload-b");
        assertEquals(DiagnosticOperationOutcome.ALLOWED, stored.replayOutcome(same));
        assertEquals(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT, stored.replayOutcome(changed));
    }

    @Test
    void requestCreationRequiresMatchingTreatingDoctorAndEligibleOpenExaminedVisit() {
        DiagnosticAuthorizationPolicy authorization = new DiagnosticAuthorizationPolicyImpl(policy);
        VisitEvidence eligible = new VisitEvidence(visitId, patientId, doctorId, true, true, true);
        assertEquals(DiagnosticOperationOutcome.ALLOWED,
                authorization.evaluateCreation(DiagnosticActor.user(doctorId, AccountRole.DOCTOR),
                        visitId, patientId, eligible));
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD,
                authorization.evaluateCreation(DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR),
                        visitId, patientId, eligible));
        assertEquals(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE,
                authorization.evaluateCreation(DiagnosticActor.user(doctorId, AccountRole.DOCTOR), visitId,
                        patientId, new VisitEvidence(visitId, patientId, doctorId, false, true, true)));
        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH,
                authorization.evaluateCreation(DiagnosticActor.user(doctorId, AccountRole.DOCTOR), visitId,
                        patientId, new VisitEvidence(visitId, UUID.randomUUID(), doctorId, true, true, true)));
    }

    @Test
    void doctorEvidenceMustMatchTheDoctorBoundToTheRequest() {
        DiagnosticRequest request = request(DiagnosticRequestStatus.RESULT_AVAILABLE, attemptWithStudy());
        DiagnosticEvidenceContext reassigned = new DiagnosticEvidenceContext(requestId, visitId, patientId,
                UUID.randomUUID(), UUID.randomUUID(), attemptId, true, true, true, false, false);
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD,
                policy.evaluate(request, DiagnosticAction.REVIEW_RESULT,
                        DiagnosticActor.user(doctorId, AccountRole.DOCTOR), reassigned, request.version()));
    }

    private void assertAllowed(DiagnosticRequest request, DiagnosticAction action,
            DiagnosticActor actor, DiagnosticEvidenceContext context) {
        DiagnosticRequestStatus originalStatus = request.status();
        long originalVersion = request.version();
        assertEquals(DiagnosticOperationOutcome.ALLOWED,
                policy.evaluate(request, action, actor, context, originalVersion));
        assertEquals(originalStatus, request.status(), "policy evaluation is read-only");
        assertEquals(originalVersion, request.version(), "policy evaluation must not advance request version");
    }

    private DiagnosticActor actorFor(DiagnosticAction action) {
        return switch (action) {
            case RECORD_FIRST_USE, LINK_CONFIRMED_STUDY -> DiagnosticActor.trustedIntegration();
            case NO_SHOW_CANCEL -> DiagnosticActor.system();
            case REVIEW_RESULT, MANUAL_CANCEL, TRANSFER_ROOM -> DiagnosticActor.user(doctorId, AccountRole.DOCTOR);
        };
    }

    private DiagnosticRequest request(DiagnosticRequestStatus status, DiagnosticAttempt attempt) {
        if (status == DiagnosticRequestStatus.RESULT_AVAILABLE || status == DiagnosticRequestStatus.REVIEWED) {
            attempt = attemptWithStudy();
        }
        return new DiagnosticRequest(requestId, visitId, patientId, doctorId, UUID.randomUUID(), "MRI",
                status, attempt, 0, now);
    }

    private DiagnosticAttempt attempt() {
        return new DiagnosticAttempt(attemptId, requestId, 1, UUID.randomUUID(), now, null);
    }

    private DiagnosticAttempt attemptWithStudy() {
        return new DiagnosticAttempt(attemptId, requestId, 1, UUID.randomUUID(), now, "opaque-study-ref");
    }

    private DiagnosticEvidenceContext evidence(boolean firstUse, boolean studyMatch, boolean noShowAccepted,
            boolean resumed, boolean firstUseRecorded) {
        return new DiagnosticEvidenceContext(requestId, visitId, patientId, doctorId, UUID.randomUUID(), attemptId,
                firstUse, studyMatch, noShowAccepted, resumed, firstUseRecorded);
    }

    private boolean requiresAttempt(DiagnosticRequestStatus status) {
        return status == DiagnosticRequestStatus.IN_PROGRESS || status == DiagnosticRequestStatus.RESULT_AVAILABLE
                || status == DiagnosticRequestStatus.REVIEWED;
    }
}
