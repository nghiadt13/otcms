package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.time.OffsetDateTime;
import java.util.UUID;

public class CreateFollowUpAppointmentCommand {
    private final UUID actorAccountId;
    private final UUID sourceVisitId;
    private final UUID serviceId;
    private final UUID doctorId;
    private final OffsetDateTime startTime;
    private final String purpose;
    private final String reason;
    private final String correlationId;

    public CreateFollowUpAppointmentCommand(UUID actorAccountId, UUID sourceVisitId, UUID serviceId, UUID doctorId,
            OffsetDateTime startTime, String purpose, String reason, String correlationId) {
        this.actorAccountId = actorAccountId; this.sourceVisitId = sourceVisitId; this.serviceId = serviceId;
        this.doctorId = doctorId; this.startTime = startTime; this.purpose = purpose; this.reason = reason;
        this.correlationId = correlationId;
    }
    public UUID actorAccountId() { return actorAccountId; }
    public UUID sourceVisitId() { return sourceVisitId; }
    public UUID serviceId() { return serviceId; }
    public UUID doctorId() { return doctorId; }
    public OffsetDateTime startTime() { return startTime; }
    public String purpose() { return purpose; }
    public String reason() { return reason; }
    public String correlationId() { return correlationId; }
}
