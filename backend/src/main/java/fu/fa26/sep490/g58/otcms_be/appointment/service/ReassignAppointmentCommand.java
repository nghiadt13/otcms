package fu.fa26.sep490.g58.otcms_be.appointment.service;


import java.util.UUID;

public class ReassignAppointmentCommand {
    private final UUID actorAccountId;
    private final UUID newDoctorProfileId;
    private final String reason;
    private final String correlationId;
    public ReassignAppointmentCommand(UUID actorAccountId, UUID newDoctorProfileId, String reason, String correlationId) {
        this.actorAccountId = actorAccountId;
        this.newDoctorProfileId = newDoctorProfileId;
        this.reason = reason;
        this.correlationId = correlationId;
    }
    public UUID getActorAccountId() { return actorAccountId; }
    public UUID actorAccountId() { return actorAccountId; }
    public UUID getNewDoctorProfileId() { return newDoctorProfileId; }
    public UUID newDoctorProfileId() { return newDoctorProfileId; }
    public String getReason() { return reason; }
    public String reason() { return reason; }
    public String getCorrelationId() { return correlationId; }
    public String correlationId() { return correlationId; }
}
