package fu.fa26.sep490.g58.otcms_be.appointment.service;


import java.time.Instant;
import java.util.UUID;

public class AppointmentSearchFilter {
    private final Instant fromInclusive;
    private final Instant toExclusive;
    private final String status;
    private final UUID doctorId;
    private final UUID patientProfileId;
    private final String patientWindow;
    private final Instant evaluatedAt;
    public AppointmentSearchFilter(Instant fromInclusive, Instant toExclusive, String status, UUID doctorId, UUID patientProfileId) {
        this(fromInclusive, toExclusive, status, doctorId, patientProfileId, null, null);
    }
    public AppointmentSearchFilter(Instant fromInclusive, Instant toExclusive, String status, UUID doctorId,
            UUID patientProfileId, String patientWindow, Instant evaluatedAt) {
        this.fromInclusive = fromInclusive;
        this.toExclusive = toExclusive;
        this.status = status;
        this.doctorId = doctorId;
        this.patientProfileId = patientProfileId;
        this.patientWindow = patientWindow;
        this.evaluatedAt = evaluatedAt;
    }
    public Instant getFromInclusive() { return fromInclusive; }
    public Instant fromInclusive() { return fromInclusive; }
    public Instant getToExclusive() { return toExclusive; }
    public Instant toExclusive() { return toExclusive; }
    public String getStatus() { return status; }
    public String status() { return status; }
    public UUID getDoctorId() { return doctorId; }
    public UUID doctorId() { return doctorId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public String patientWindow() { return patientWindow; }
    public Instant evaluatedAt() { return evaluatedAt; }
}
