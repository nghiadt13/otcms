package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.time.Instant;
import java.util.UUID;

public class NoShowAppointment {
    private final UUID id;
    private final UUID patientProfileId;
    private final UUID doctorProfileId;
    private final String status;
    private final Instant startTime;
    private final Instant endTime;
    private final UUID visitId;
    private final UUID sourceVisitId;

    public NoShowAppointment(UUID id, UUID patientProfileId, UUID doctorProfileId, String status,
            Instant startTime, Instant endTime, UUID visitId, UUID sourceVisitId) {
        this.id = id;
        this.patientProfileId = patientProfileId;
        this.doctorProfileId = doctorProfileId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.visitId = visitId;
        this.sourceVisitId = sourceVisitId;
    }

    public UUID id() { return id; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public String status() { return status; }
    public Instant startTime() { return startTime; }
    public Instant endTime() { return endTime; }
    public UUID visitId() { return visitId; }
    public UUID sourceVisitId() { return sourceVisitId; }
}
