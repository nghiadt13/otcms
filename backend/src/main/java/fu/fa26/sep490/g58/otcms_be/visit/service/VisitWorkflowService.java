package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;
import java.util.UUID;

/** Visit/clinical-owned evidence required to authorize a Doctor follow-up. */
    public interface VisitWorkflowService {
    FollowUpEvidence requireFollowUpEvidence(UUID visitId, UUID authenticatedDoctorProfileId);


        static class FollowUpEvidence {
    private final UUID visitId;
    private final UUID patientProfileId;
    private final UUID authenticatedDoctorProfileId;
    private final boolean doctorAssignedToCare;
    private final boolean examinationCompleted;
    private final boolean followUpIndicated;
    private final UUID originatingAppointmentId;
    private final Instant evidenceAsOf;
    public FollowUpEvidence(UUID visitId, UUID patientProfileId, UUID authenticatedDoctorProfileId, boolean doctorAssignedToCare, boolean examinationCompleted, boolean followUpIndicated, UUID originatingAppointmentId, Instant evidenceAsOf) {
        this.visitId = visitId;
        this.patientProfileId = patientProfileId;
        this.authenticatedDoctorProfileId = authenticatedDoctorProfileId;
        this.doctorAssignedToCare = doctorAssignedToCare;
        this.examinationCompleted = examinationCompleted;
        this.followUpIndicated = followUpIndicated;
        this.originatingAppointmentId = originatingAppointmentId;
        this.evidenceAsOf = evidenceAsOf;
    }
    public UUID getVisitId() { return visitId; }
    public UUID visitId() { return visitId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID getAuthenticatedDoctorProfileId() { return authenticatedDoctorProfileId; }
    public UUID authenticatedDoctorProfileId() { return authenticatedDoctorProfileId; }
    public boolean getDoctorAssignedToCare() { return doctorAssignedToCare; }
    public boolean isDoctorAssignedToCare() { return doctorAssignedToCare; }
    public boolean doctorAssignedToCare() { return doctorAssignedToCare; }
    public boolean getExaminationCompleted() { return examinationCompleted; }
    public boolean isExaminationCompleted() { return examinationCompleted; }
    public boolean examinationCompleted() { return examinationCompleted; }
    public boolean getFollowUpIndicated() { return followUpIndicated; }
    public boolean isFollowUpIndicated() { return followUpIndicated; }
    public boolean followUpIndicated() { return followUpIndicated; }
    public UUID getOriginatingAppointmentId() { return originatingAppointmentId; }
    public UUID originatingAppointmentId() { return originatingAppointmentId; }
    public Instant getEvidenceAsOf() { return evidenceAsOf; }
    public Instant evidenceAsOf() { return evidenceAsOf; }
}
}
