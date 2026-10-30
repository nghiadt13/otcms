package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;
import java.util.UUID;

/** Reception-owned arrival evidence and atomic reception/visit workflow boundary. */
    public interface ReceptionWorkflowService {
    CheckInResult checkInAtomically(CheckInCommand command);

    /** Verifies that a linked scheduled visit belongs to the appointment/patient and has initial reception evidence. */
    boolean hasConsistentCheckInEvidence(UUID appointmentId, UUID patientProfileId, UUID visitId);

        static class CheckInCommand {
    private final UUID actorAccountId;
    private final UUID appointmentId;
    private final UUID patientProfileId;
    private final String idempotencyKey;
    private final Instant allowedWindowStartInclusive;
    private final Instant allowedWindowEndInclusive;
    public CheckInCommand(UUID actorAccountId, UUID appointmentId, UUID patientProfileId, String idempotencyKey, Instant allowedWindowStartInclusive, Instant allowedWindowEndInclusive) {
        this.actorAccountId = actorAccountId;
        this.appointmentId = appointmentId;
        this.patientProfileId = patientProfileId;
        this.idempotencyKey = idempotencyKey;
        this.allowedWindowStartInclusive = allowedWindowStartInclusive;
        this.allowedWindowEndInclusive = allowedWindowEndInclusive;
    }
    public UUID getActorAccountId() { return actorAccountId; }
    public UUID actorAccountId() { return actorAccountId; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID appointmentId() { return appointmentId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String idempotencyKey() { return idempotencyKey; }
    public Instant getAllowedWindowStartInclusive() { return allowedWindowStartInclusive; }
    public Instant allowedWindowStartInclusive() { return allowedWindowStartInclusive; }
    public Instant getAllowedWindowEndInclusive() { return allowedWindowEndInclusive; }
    public Instant allowedWindowEndInclusive() { return allowedWindowEndInclusive; }
}

        static class CheckInResult {
    private final UUID appointmentId;
    private final UUID visitId;
    private final UUID patientProfileId;
    private final Instant arrivedAt;
    private final Instant checkedInAt;
    private final boolean replayed;
    public CheckInResult(UUID appointmentId, UUID visitId, UUID patientProfileId, Instant arrivedAt, Instant checkedInAt, boolean replayed) {
        this.appointmentId = appointmentId;
        this.visitId = visitId;
        this.patientProfileId = patientProfileId;
        this.arrivedAt = arrivedAt;
        this.checkedInAt = checkedInAt;
        this.replayed = replayed;
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
}
}
