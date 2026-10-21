package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.time.Instant;
import java.util.UUID;

public class CheckInAppointment {
    private final UUID id;
    private final UUID patientProfileId;
    private final UUID doctorProfileId;
    private final String status;
    private final UUID visitId;
    private final UUID sourceVisitId;
    private final Instant startTime;
    public CheckInAppointment(UUID id, UUID patientProfileId, UUID doctorProfileId, String status, UUID visitId, UUID sourceVisitId, Instant startTime) {
        this.id = id;
        this.patientProfileId = patientProfileId;
        this.doctorProfileId = doctorProfileId;
        this.status = status;
        this.visitId = visitId;
        this.sourceVisitId = sourceVisitId;
        this.startTime = startTime;
    }
    public UUID getId() { return id; }
    public UUID id() { return id; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public String getStatus() { return status; }
    public String status() { return status; }
    public UUID getVisitId() { return visitId; }
    public UUID visitId() { return visitId; }
    public UUID getSourceVisitId() { return sourceVisitId; }
    public UUID sourceVisitId() { return sourceVisitId; }
    public Instant getStartTime() { return startTime; }
    public Instant startTime() { return startTime; }
}
