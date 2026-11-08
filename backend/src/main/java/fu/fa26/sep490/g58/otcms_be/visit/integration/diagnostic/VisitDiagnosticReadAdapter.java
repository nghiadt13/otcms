package fu.fa26.sep490.g58.otcms_be.visit.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticReadPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitImagingHistoryAuthorizationPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Visit-owned current-care and imaging-history authorization reads; lifecycle result commands remain separate. */
@Component
public class VisitDiagnosticReadAdapter implements VisitDiagnosticReadPort, VisitImagingHistoryAuthorizationPort {
    private final VisitClinicalCareContextService visits;
    private final DoctorProfileIdentityQueryService identities;
    private final DoctorAccountAccessService accounts;

    public VisitDiagnosticReadAdapter(VisitClinicalCareContextService visits,
            DoctorProfileIdentityQueryService identities, DoctorAccountAccessService accounts) {
        this.visits = visits; this.identities = identities; this.accounts = accounts;
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

    @Override
    public VisitHistoryAuthorization authorizeImagingHistory(UUID visitId, UUID actorAccountId) {
        var context = visits.findDoctorImagingHistoryContext(visitId).orElse(null);
        if (context == null) return denied(visitId, actorAccountId);
        var doctor = actorAccountId == null ? null : identities.findDoctorProfileId(actorAccountId).orElse(null);
        UUID treatingDoctorAccountId = identities.findDoctorAccountId(context.doctorProfileId()).orElse(null);
        if (doctor == null) {
            // Patient history still needs only the Visit-to-Patient link; Patient owner authorization is separate.
            return new VisitHistoryAuthorization(context.visitId(), context.patientProfileId(),
                    treatingDoctorAccountId, null, context.visitStatus(), true);
        }
        if (!accounts.isActiveDoctorAccount(actorAccountId) || !doctor.equals(context.doctorProfileId())) {
            return new VisitHistoryAuthorization(visitId, context.patientProfileId(),
                    treatingDoctorAccountId, null, context.visitStatus(), false);
        }
        return new VisitHistoryAuthorization(context.visitId(), context.patientProfileId(), actorAccountId,
                context.examinationEvidenceId(), context.visitStatus(), true);
    }

    private static VisitHistoryAuthorization denied(UUID visitId, UUID actorAccountId) {
        return new VisitHistoryAuthorization(visitId, null, actorAccountId, null, null, false);
    }

    private static DiagnosticOperationException failure(DiagnosticOperationOutcome outcome) {
        return new DiagnosticOperationException(outcome);
    }
}
