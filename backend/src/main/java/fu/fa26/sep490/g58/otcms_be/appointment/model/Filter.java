package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.time.Instant;
import java.util.UUID;

public class Filter {
    private final Instant fromInclusive;
    private final Instant toExclusive;
    private final String status;
    private final UUID doctorId;
    private final UUID patientProfileId;
    public Filter(Instant fromInclusive, Instant toExclusive, String status, UUID doctorId, UUID patientProfileId) {
        this.fromInclusive = fromInclusive;
        this.toExclusive = toExclusive;
        this.status = status;
        this.doctorId = doctorId;
        this.patientProfileId = patientProfileId;
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
}
