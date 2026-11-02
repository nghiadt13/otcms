package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Visit-owned active Doctor care intervals, bounded to requested Doctors and time. */
    public interface VisitCareReservationQueryService {
    List<CareReservation> findActiveCareReservations(Collection<UUID> doctorProfileIds,
                                                     Instant fromInclusive, Instant toExclusive);

        static class CareReservation {
    private final UUID assignmentId;
    private final UUID doctorProfileId;
    private final UUID patientProfileId;
    private final UUID linkedAppointmentId;
    private final Instant startsAt;
    private final Instant endsAt;
    public CareReservation(UUID assignmentId, UUID doctorProfileId, UUID patientProfileId, UUID linkedAppointmentId, Instant startsAt, Instant endsAt) {
        this.assignmentId = assignmentId;
        this.doctorProfileId = doctorProfileId;
        this.patientProfileId = patientProfileId;
        this.linkedAppointmentId = linkedAppointmentId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
    public UUID getAssignmentId() { return assignmentId; }
    public UUID assignmentId() { return assignmentId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID getLinkedAppointmentId() { return linkedAppointmentId; }
    public UUID linkedAppointmentId() { return linkedAppointmentId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant startsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant endsAt() { return endsAt; }
}
}
