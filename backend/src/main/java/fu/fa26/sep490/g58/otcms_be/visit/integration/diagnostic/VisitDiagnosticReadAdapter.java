package fu.fa26.sep490.g58.otcms_be.visit.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitImagingCoordinationService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Single Visit-owned Diagnostic adapter; current-care reads and durable result coordination. */
@Component
public class VisitDiagnosticReadAdapter implements VisitDiagnosticPort {
    private final VisitClinicalCareContextService visits;
    private final DoctorProfileIdentityQueryService identities;
    private final DoctorAccountAccessService accounts;
    private final VisitImagingCoordinationService coordination;
    private final PatientIdentityDirectory patients;

    public VisitDiagnosticReadAdapter(VisitClinicalCareContextService visits,
            DoctorProfileIdentityQueryService identities, DoctorAccountAccessService accounts,
            VisitImagingCoordinationService coordination, PatientIdentityDirectory patients) {
        this.visits = visits; this.identities = identities; this.accounts = accounts;
        this.coordination = coordination; this.patients = patients;
    }

    @Override
    public VisitEvidence requireImagingOrderEligibility(UUID visitId, UUID actorAccountId) {
        var context = visits.findClinicalCareContext(visitId).orElseThrow(() -> failure(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        var doctor = actorAccountId == null ? null : identities.findDoctorProfileId(actorAccountId).orElse(null);
        if (doctor == null || !accounts.isActiveDoctorAccount(actorAccountId)
                || !doctor.equals(context.responsibleDoctorProfileId())) {
            throw failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        }
        return new VisitEvidence(context.visitId(), context.patientProfileId(), actorAccountId,
                context.visitOpen(), context.activeCareAssignmentId() != null, context.clinicalWorkAllowed());
    }

    @Override
    public VisitStatusAuthorization authorizeImagingStatus(UUID visitId, UUID actorAccountId,
            VisitStatusResponsibility responsibility) {
        if (responsibility != VisitStatusResponsibility.DOCTOR) throw failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        var evidence = requireImagingOrderEligibility(visitId, actorAccountId);
        return new VisitStatusAuthorization(visitId, actorAccountId, responsibility,
                evidence.open() && evidence.eligible() && evidence.examinationStarted());
    }

    private static DiagnosticOperationException failure(DiagnosticOperationOutcome outcome) {
        return new DiagnosticOperationException(outcome);
    }

    @Override public void publishResultReady(UUID requestId, UUID visitId, UUID eventId) {
        try { coordination.recordResultReady(requestId, visitId, eventId); }
        catch (VisitOperationException exception) { throw map(exception); }
    }

    @Override public ReleaseAcknowledgement publishFindingsReleased(FindingsReleaseEvent event) {
        try {
            String hash = coordination.acknowledgeFindingsRelease(event);
            return new ReleaseAcknowledgement(event.eventId(), event.requestId(), hash, true);
        } catch (VisitOperationException exception) { throw map(exception); }
    }

    @Override public VisitHistoryAuthorization authorizeImagingHistory(UUID visitId, UUID actorId) {
        if (actorId == null) throw failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        boolean doctor = accounts.isActiveDoctorAccount(actorId);
        UUID profile = doctor ? identities.findDoctorProfileId(actorId).orElseThrow(
                () -> failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD)) : null;
        if (!doctor && !patients.isActivePatient(actorId)) throw failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        try {
            var history = coordination.historyEvidence(visitId, profile);
            // Patient access is only Visit-to-Patient linkage here; Diagnostic must independently
            // require PatientDiagnosticHistoryPort own/dependent permission before querying results.
            return new VisitHistoryAuthorization(history.visitId(), history.patientId(), doctor ? actorId : null,
                    history.careAssignmentId(), history.status(), !doctor || history.careAssignmentId() != null);
        } catch (VisitOperationException exception) { throw map(exception); }
    }

    @Override public NoShowDecision acceptDeferredNoShowCancellation(UUID requestId, UUID visitId, Instant deadline, UUID eventId) {
        // No current owner command implements this separate workflow. Never invent resume/first-use facts.
        throw failure(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
    }

    private static DiagnosticOperationException map(VisitOperationException error) {
        return failure(switch (error.code()) {
            case "RESOURCE_NOT_FOUND" -> DiagnosticOperationOutcome.RECORD_NOT_FOUND;
            case "IDEMPOTENCY_CONFLICT" -> DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT;
            case "EVIDENCE_MISMATCH" -> DiagnosticOperationOutcome.EVIDENCE_MISMATCH;
            default -> DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE;
        });
    }
}
