package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.time.Instant;
import java.util.UUID;

public class ReschedulableAppointment {
    private final UUID id;
    private final UUID patientProfileId;
    private final UUID serviceId;
    private final UUID doctorProfileId;
    private final String status;
    private final String purpose;
    private final Instant startTime;
    private final Instant endTime;
    private final int durationMinutes;
    private final int bufferMinutes;
    private final String bookingSource;
    private final java.util.UUID visitId;
    private final java.util.UUID sourceVisitId;
    private final Instant createdAt;
    public ReschedulableAppointment(UUID id, UUID patientProfileId, UUID serviceId, UUID doctorProfileId, String status, String purpose, Instant startTime, Instant endTime, int durationMinutes, int bufferMinutes, String bookingSource, java.util.UUID visitId, java.util.UUID sourceVisitId, Instant createdAt) {
        this.id = id;
        this.patientProfileId = patientProfileId;
        this.serviceId = serviceId;
        this.doctorProfileId = doctorProfileId;
        this.status = status;
        this.purpose = purpose;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
        this.bufferMinutes = bufferMinutes;
        this.bookingSource = bookingSource;
        this.visitId = visitId;
        this.sourceVisitId = sourceVisitId;
        this.createdAt = createdAt;
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
    public String getPurpose() { return purpose; }
    public String purpose() { return purpose; }
    public Instant getStartTime() { return startTime; }
    public Instant startTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Instant endTime() { return endTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public int durationMinutes() { return durationMinutes; }
    public int getBufferMinutes() { return bufferMinutes; }
    public int bufferMinutes() { return bufferMinutes; }
    public String getBookingSource() { return bookingSource; }
    public String bookingSource() { return bookingSource; }
    public java.util.UUID getVisitId() { return visitId; }
    public java.util.UUID visitId() { return visitId; }
    public java.util.UUID getSourceVisitId() { return sourceVisitId; }
    public java.util.UUID sourceVisitId() { return sourceVisitId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant createdAt() { return createdAt; }
}
