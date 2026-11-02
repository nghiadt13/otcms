package fu.fa26.sep490.g58.otcms_be.visit.model;

import java.time.Instant;
import java.util.UUID;

public class CreatedWalkInVisit {
    private final UUID visitId;
    private final UUID patientProfileId;
    private final UUID priorAppointmentId;
    private final Instant arrivedAt;

    public CreatedWalkInVisit(UUID visitId, UUID patientProfileId, UUID priorAppointmentId, Instant arrivedAt) {
        this.visitId = visitId;
        this.patientProfileId = patientProfileId;
        this.priorAppointmentId = priorAppointmentId;
        this.arrivedAt = arrivedAt;
    }

    public UUID visitId() { return visitId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID priorAppointmentId() { return priorAppointmentId; }
    public Instant arrivedAt() { return arrivedAt; }
}
