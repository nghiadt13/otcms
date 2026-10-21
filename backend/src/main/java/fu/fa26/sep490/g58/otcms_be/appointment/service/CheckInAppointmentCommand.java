package fu.fa26.sep490.g58.otcms_be.appointment.service;


import java.util.UUID;

public class CheckInAppointmentCommand {
    private final UUID actorAccountId;
    private final String idempotencyKey;
    private final String correlationId;
    public CheckInAppointmentCommand(UUID actorAccountId, String idempotencyKey, String correlationId) {
        this.actorAccountId = actorAccountId;
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
    }
    public UUID getActorAccountId() { return actorAccountId; }
    public UUID actorAccountId() { return actorAccountId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String idempotencyKey() { return idempotencyKey; }
    public String getCorrelationId() { return correlationId; }
    public String correlationId() { return correlationId; }
}
