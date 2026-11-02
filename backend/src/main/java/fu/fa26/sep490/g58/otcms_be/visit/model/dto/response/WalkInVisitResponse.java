package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WalkInResponse")
public class WalkInVisitResponse {
    private final UUID visitId;
    private final UUID patientProfileId;
    private final UUID priorAppointmentId;
    private final Instant arrivedAt;

    public WalkInVisitResponse(UUID visitId, UUID patientProfileId, UUID priorAppointmentId, Instant arrivedAt) {
        this.visitId = visitId;
        this.patientProfileId = patientProfileId;
        this.priorAppointmentId = priorAppointmentId;
        this.arrivedAt = arrivedAt;
    }

    public UUID getVisitId() { return visitId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    @Schema(nullable = true)
    public UUID getPriorAppointmentId() { return priorAppointmentId; }
    public Instant getArrivedAt() { return arrivedAt; }
}
