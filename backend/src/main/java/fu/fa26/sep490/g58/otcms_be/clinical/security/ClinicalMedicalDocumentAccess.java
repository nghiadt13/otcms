package fu.fa26.sep490.g58.otcms_be.clinical.security;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitDoctorCareRelationQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ClinicalMedicalDocumentAccess {
    public record Scope(UUID visitId, UUID patientId, UUID doctorProfileId, AccountRole role) { }
    private final ClinicalDoctorIdentityResolver doctors;
    private final VisitClinicalContextPort clinicalVisits;
    private final VisitClinicalCareContextService visits;
    private final ReceptionistAccountAccessService receptionists;
    private final PatientProfileAccessService patients;
    private final VisitDoctorCareRelationQueryService care;
    private final VisitReviewSourceService visitSources;
    private final VisitExamQueueService examQueue;

    public ClinicalMedicalDocumentAccess(ClinicalDoctorIdentityResolver doctors, VisitClinicalContextPort clinicalVisits,
            VisitClinicalCareContextService visits, ReceptionistAccountAccessService receptionists, PatientProfileAccessService patients,
            VisitDoctorCareRelationQueryService care, VisitReviewSourceService visitSources, VisitExamQueueService examQueue) {
        this.doctors = doctors; this.clinicalVisits = clinicalVisits; this.visits = visits;
        this.receptionists = receptionists; this.patients = patients;
        this.care = care; this.visitSources = visitSources; this.examQueue = examQueue;
    }
    public Scope doctor(AuthenticatedAccount actor, UUID visitId) {
        UUID doctorId = doctors.requireDoctorProfile(actor);
        if (visitId == null) throw error("MSG-39");
        var context = clinicalVisits.getVisitContext(visitId);
        if (context == null) throw error("MSG-41");
        if (!doctorId.equals(context.responsibleDoctorId())) throw error("MSG-40");
        if (!context.clinicalWorkAllowed()) throw error("MSG-42");
        if (!visitId.equals(context.visitId()) || context.patientId() == null) throw error("MSG-41");
        return new Scope(visitId, context.patientId(), doctorId, AccountRole.DOCTOR);
    }
    public Scope read(AuthenticatedAccount actor, UUID visitId) {
        requireActor(actor);
        if (actor.accountRole() != AccountRole.DOCTOR && actor.accountRole() != AccountRole.RECEPTIONIST && actor.accountRole() != AccountRole.PATIENT) throw error("MSG-40");
        if (actor.accountRole() == AccountRole.RECEPTIONIST && !receptionists.isActiveReceptionistAccount(actor.accountId())) throw error("MSG-40");
        if (visitId == null) throw error("MSG-39");
        var visit = visits.findClinicalCareContext(visitId).orElseThrow(() -> error("MSG-41"));
        if (!visitId.equals(visit.visitId()) || visit.patientProfileId() == null) throw error("MSG-41");
        if (actor.accountRole() == AccountRole.DOCTOR) {
            UUID doctorId = doctors.requireDoctorProfile(actor);
            if (!(doctorId.equals(visit.responsibleDoctorProfileId()) && visit.clinicalWorkAllowed())
                    && !care.hasCurrentCare(doctorId, visit.patientProfileId())) throw error("MSG-40");
            return new Scope(visitId, visit.patientProfileId(), doctorId, AccountRole.DOCTOR);
        }
        if (actor.accountRole() == AccountRole.RECEPTIONIST && !visit.visitOpen()) throw error("MSG-42");
        if (actor.accountRole() == AccountRole.PATIENT && !patients.canAccess(actor.accountId(), visit.patientProfileId())) throw error("MSG-40");
        return new Scope(visitId, visit.patientProfileId(), null, actor.accountRole());
    }
    /** Closed result sheets remain available to authorized staff and patients. */
    public Scope resultSheet(AuthenticatedAccount actor, UUID visitId, boolean requireClosed) {
        requireActor(actor);
        if (visitId == null) throw error("MSG-39");
        var visit = visitSources.find(visitId).orElseThrow(() -> error("MSG-41"));
        if (!"CLOSED".equals(visit.status())) {
            if (requireClosed || actor.accountRole() == AccountRole.PATIENT) throw error("MSG-42");
            return read(actor, visitId);
        }
        if (actor.accountRole() == AccountRole.PATIENT) {
            if (!patients.canAccess(actor.accountId(), visit.patientProfileId())) throw error("MSG-40");
            return new Scope(visitId, visit.patientProfileId(), null, AccountRole.PATIENT);
        }
        if (actor.accountRole() == AccountRole.RECEPTIONIST) {
            if (!receptionists.isActiveReceptionistAccount(actor.accountId())) throw error("MSG-40");
            return new Scope(visitId, visit.patientProfileId(), null, AccountRole.RECEPTIONIST);
        }
        if (actor.accountRole() == AccountRole.DOCTOR) {
            UUID doctorId = doctors.requireDoctorProfile(actor);
            boolean responsible = examQueue.find(visitId).map(q -> doctorId.equals(q.doctorProfileId())).orElse(false);
            if (!responsible && !care.hasCurrentCare(doctorId, visit.patientProfileId())) throw error("MSG-40");
            return new Scope(visitId, visit.patientProfileId(), doctorId, AccountRole.DOCTOR);
        }
        throw error("MSG-40");
    }
    public boolean isClosed(UUID visitId) {
        return visitSources.find(visitId).map(visit -> "CLOSED".equals(visit.status())).orElse(false);
    }
    public Scope regenerateResultSheet(AuthenticatedAccount actor, UUID visitId) {
        UUID doctorId = doctors.requireDoctorProfile(actor);
        Scope scope = resultSheet(actor, visitId, true);
        if (!examQueue.find(visitId).map(q -> doctorId.equals(q.doctorProfileId())).orElse(false)) throw error("MSG-40");
        return scope;
    }
    /** UC-38: only the attending doctor of a CLOSED visit may amend its result. */
    public Scope amendResult(AuthenticatedAccount actor, UUID visitId) {
        UUID doctorId = doctors.requireDoctorProfile(actor);
        if (visitId == null) throw error("MSG-39");
        var visit = visitSources.find(visitId).orElseThrow(() -> error("MSG-41"));
        if (!examQueue.find(visitId).map(q -> doctorId.equals(q.doctorProfileId())).orElse(false)) throw error("MSG-40");
        if (!"CLOSED".equals(visit.status())) throw error("MSG-42");
        return new Scope(visitId, visit.patientProfileId(), doctorId, AccountRole.DOCTOR);
    }
    public Scope upload(AuthenticatedAccount actor, UUID visitId) {
        requireActor(actor);
        if (actor.accountRole() == AccountRole.DOCTOR) return doctor(actor, visitId);
        if (actor.accountRole() != AccountRole.RECEPTIONIST) throw error("MSG-40");
        return read(actor, visitId);
    }
    private static void requireActor(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() == null) throw error("MSG-40");
    }
    private static ClinicalOperationException error(String code) { return new ClinicalOperationException(code); }
}
