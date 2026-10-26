package fu.fa26.sep490.g58.otcms_be.appointment.service;


import java.time.OffsetDateTime;
import java.util.UUID;

public class CreateAppointmentCommand {
    private final UUID actorAccountId;
    private final Actor actor;
    private final UUID patientProfileId;
    private final UUID serviceId;
    private final UUID doctorProfileId;
    private final OffsetDateTime startTime;
    private final String purpose;
    private final String correlationId;
    public CreateAppointmentCommand(UUID actorAccountId, Actor actor, UUID patientProfileId, UUID serviceId, UUID doctorProfileId, OffsetDateTime startTime, String purpose, String correlationId) {
        this.actorAccountId = actorAccountId;
        this.actor = actor;
        this.patientProfileId = patientProfileId;
        this.serviceId = serviceId;
        this.doctorProfileId = doctorProfileId;
        this.startTime = startTime;
        this.purpose = purpose;
        this.correlationId = correlationId;
    }
    public UUID getActorAccountId() { return actorAccountId; }
    public UUID actorAccountId() { return actorAccountId; }
    public Actor getActor() { return actor; }
    public Actor actor() { return actor; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID getServiceId() { return serviceId; }
    public UUID serviceId() { return serviceId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public OffsetDateTime getStartTime() { return startTime; }
    public OffsetDateTime startTime() { return startTime; }
    public String getPurpose() { return purpose; }
    public String purpose() { return purpose; }
    public String getCorrelationId() { return correlationId; }
    public String correlationId() { return correlationId; }
    public enum Actor { PATIENT, RECEPTIONIST }
}
