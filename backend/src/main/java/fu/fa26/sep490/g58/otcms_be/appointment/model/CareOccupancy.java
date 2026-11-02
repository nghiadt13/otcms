package fu.fa26.sep490.g58.otcms_be.appointment.model;


import java.util.UUID;

public class CareOccupancy {
    private final UUID assignmentId;
    private final UUID doctorProfileId;
    private final UUID patientProfileId;
    private final UUID linkedAppointmentId;
    private final AvailabilityInterval interval;
    public CareOccupancy(UUID assignmentId, UUID doctorProfileId, UUID patientProfileId, UUID linkedAppointmentId, AvailabilityInterval interval) {
        this.assignmentId = assignmentId;
        this.doctorProfileId = doctorProfileId;
        this.patientProfileId = patientProfileId;
        this.linkedAppointmentId = linkedAppointmentId;
        this.interval = interval;
    }
    public UUID getAssignmentId() { return assignmentId; }
    public UUID assignmentId() { return assignmentId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID getLinkedAppointmentId() { return linkedAppointmentId; }
    public UUID linkedAppointmentId() { return linkedAppointmentId; }
    public AvailabilityInterval getInterval() { return interval; }
    public AvailabilityInterval interval() { return interval; }
}
