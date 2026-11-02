package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.util.UUID;

public class UpdateAppointmentStatusCommand {
    private final UUID actorAccountId;
    private final String actorType;
    private final String targetStatus;
    private final String correlationId;
    public UpdateAppointmentStatusCommand(UUID actorAccountId, String actorType, String targetStatus, String correlationId) {
        this.actorAccountId = actorAccountId; this.actorType = actorType;
        this.targetStatus = targetStatus; this.correlationId = correlationId;
    }
    public UUID actorAccountId() { return actorAccountId; }
    public String actorType() { return actorType; }
    public String targetStatus() { return targetStatus; }
    public String correlationId() { return correlationId; }
}
