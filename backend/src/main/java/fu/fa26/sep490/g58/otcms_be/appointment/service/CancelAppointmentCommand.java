package fu.fa26.sep490.g58.otcms_be.appointment.service;


import java.util.UUID;

public class CancelAppointmentCommand {
    private final UUID actorAccountId;
    private final Actor actor;
    private final String reason;
    private final String correlationId;
    private final boolean clinicFault;
    public CancelAppointmentCommand(UUID actorAccountId, Actor actor, String reason, String correlationId) {
        this(actorAccountId, actor, reason, correlationId, false);
    }
    /** @param clinicFault staff only: the clinic cancels (e.g. doctor absent, nobody to replace), so a paid fee awaits a manual refund. */
    public CancelAppointmentCommand(UUID actorAccountId, Actor actor, String reason, String correlationId, boolean clinicFault) {
        this.clinicFault = clinicFault;
        this.actorAccountId = actorAccountId;
        this.actor = actor;
        this.reason = reason;
        this.correlationId = correlationId;
    }
    public UUID getActorAccountId() { return actorAccountId; }
    public UUID actorAccountId() { return actorAccountId; }
    public Actor getActor() { return actor; }
    public Actor actor() { return actor; }
    public String getReason() { return reason; }
    public String reason() { return reason; }
    public String getCorrelationId() { return correlationId; }
    public String correlationId() { return correlationId; }
    public boolean clinicFault() { return clinicFault; }
    public enum Actor { PATIENT, RECEPTIONIST, ADMIN }
}
