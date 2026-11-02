package fu.fa26.sep490.g58.otcms_be.appointment.service;


import java.time.OffsetDateTime;
import java.util.UUID;

public class RescheduleAppointmentCommand {
    private final UUID actorAccountId;
    private final Actor actor;
    private final OffsetDateTime startTime;
    private final String reason;
    private final String correlationId;
    public RescheduleAppointmentCommand(UUID actorAccountId, Actor actor, OffsetDateTime startTime, String reason, String correlationId) {
        this.actorAccountId = actorAccountId;
        this.actor = actor;
        this.startTime = startTime;
        this.reason = reason;
        this.correlationId = correlationId;
    }
    public UUID getActorAccountId() { return actorAccountId; }
    public UUID actorAccountId() { return actorAccountId; }
    public Actor getActor() { return actor; }
    public Actor actor() { return actor; }
    public OffsetDateTime getStartTime() { return startTime; }
    public OffsetDateTime startTime() { return startTime; }
    public String getReason() { return reason; }
    public String reason() { return reason; }
    public String getCorrelationId() { return correlationId; }
    public String correlationId() { return correlationId; }
    public enum Actor { PATIENT, RECEPTIONIST }
}
