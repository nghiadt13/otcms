package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.time.Instant;
import java.util.UUID;

public class AppointmentChange {
    private final UUID id;
    private final UUID patientProfileId;
    private final UUID serviceId;
    private final UUID doctorProfileId;
    private final String status;
    private final Instant startTime;
    private final int durationMinutes;
    private final int bufferMinutes;
    public AppointmentChange(UUID id, UUID patientProfileId, UUID serviceId, UUID doctorProfileId, String status, Instant startTime, int durationMinutes, int bufferMinutes) {
        this.id = id;
        this.patientProfileId = patientProfileId;
        this.serviceId = serviceId;
        this.doctorProfileId = doctorProfileId;
        this.status = status;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.bufferMinutes = bufferMinutes;
    }
    public UUID getId() { return id; }
    public UUID id() { return id; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID getServiceId() { return serviceId; }
    public UUID serviceId() { return serviceId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public String getStatus() { return status; }
    public String status() { return status; }
    public Instant getStartTime() { return startTime; }
    public Instant startTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public int durationMinutes() { return durationMinutes; }
    public int getBufferMinutes() { return bufferMinutes; }
    public int bufferMinutes() { return bufferMinutes; }
}
