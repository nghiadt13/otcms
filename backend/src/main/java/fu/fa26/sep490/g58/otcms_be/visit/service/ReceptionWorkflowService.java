package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueTicket;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reception-owned arrival evidence and atomic reception/visit workflow boundary. */
    public interface ReceptionWorkflowService {
    CheckInResult checkInAtomically(CheckInCommand command);

    /** Verifies that a linked scheduled visit belongs to the appointment/patient and has initial reception evidence. */
    boolean hasConsistentCheckInEvidence(UUID appointmentId, UUID patientProfileId, UUID visitId);

    /**
     * Queue number, doctor and room of a visit's exam-queue row, shown after check-in and on replays.
     * Empty when the visit has no queue row (for example a visit closed before the queue table existed).
     */
    Optional<ExamQueueTicket> findExamQueueTicket(UUID visitId);

        static class CheckInCommand {
    private final UUID actorAccountId;
    private final UUID appointmentId;
    private final UUID patientProfileId;
    private final UUID doctorProfileId;
    private final String idempotencyKey;
    public CheckInCommand(UUID actorAccountId, UUID appointmentId, UUID patientProfileId, UUID doctorProfileId,
            String idempotencyKey) {
        this.actorAccountId = actorAccountId;
        this.appointmentId = appointmentId;
        this.patientProfileId = patientProfileId;
        this.doctorProfileId = doctorProfileId;
        this.idempotencyKey = idempotencyKey;
    }
    public UUID getActorAccountId() { return actorAccountId; }
    public UUID actorAccountId() { return actorAccountId; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID appointmentId() { return appointmentId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    /** Doctor of the appointment; the visit joins the exam queue of this doctor. */
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String idempotencyKey() { return idempotencyKey; }
}

        static class CheckInResult {
    private final UUID appointmentId;
    private final UUID visitId;
    private final UUID patientProfileId;
    private final Instant arrivedAt;
    private final Instant checkedInAt;
    private final boolean replayed;
    private final ExamQueueTicket ticket;
    public CheckInResult(UUID appointmentId, UUID visitId, UUID patientProfileId, Instant arrivedAt, Instant checkedInAt, boolean replayed, ExamQueueTicket ticket) {
        this.appointmentId = appointmentId;
        this.visitId = visitId;
        this.patientProfileId = patientProfileId;
        this.arrivedAt = arrivedAt;
        this.checkedInAt = checkedInAt;
        this.replayed = replayed;
        this.ticket = ticket;
    }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID appointmentId() { return appointmentId; }
    public UUID getVisitId() { return visitId; }
    public UUID visitId() { return visitId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public Instant getArrivedAt() { return arrivedAt; }
    public Instant arrivedAt() { return arrivedAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant checkedInAt() { return checkedInAt; }
    public boolean getReplayed() { return replayed; }
    public boolean isReplayed() { return replayed; }
    public boolean replayed() { return replayed; }
    /** Queue number, doctor and room granted by this check-in. */
    public ExamQueueTicket getTicket() { return ticket; }
    public ExamQueueTicket ticket() { return ticket; }
}
}
