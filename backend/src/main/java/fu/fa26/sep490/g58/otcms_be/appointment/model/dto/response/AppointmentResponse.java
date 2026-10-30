package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class AppointmentResponse {
    private final UUID id;
    private final UUID patientProfileId;
    private final UUID serviceId;
    private final String serviceName;
    private final BigDecimal servicePrice;
    private final UUID doctorId;
    private final String doctorDisplayName;
    private final String status;
    private final String bookingSource;
    private final String purpose;
    private final Instant startTime;
    private final Instant endTime;
    private final int durationMinutes;
    private final int bufferMinutes;
    private final Instant occupiedUntil;
    private final UUID visitId;
    private final UUID sourceVisitId;
    private final Instant createdAt;
    private final Instant updatedAt;
    public AppointmentResponse(UUID id, UUID patientProfileId, UUID serviceId, String serviceName, BigDecimal servicePrice, UUID doctorId, String doctorDisplayName, String status, String bookingSource, String purpose, Instant startTime, Instant endTime, int durationMinutes, int bufferMinutes, Instant occupiedUntil, UUID visitId, UUID sourceVisitId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.patientProfileId = patientProfileId;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.servicePrice = servicePrice;
        this.doctorId = doctorId;
        this.doctorDisplayName = doctorDisplayName;
        this.status = status;
        this.bookingSource = bookingSource;
        this.purpose = purpose;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
        this.bufferMinutes = bufferMinutes;
        this.occupiedUntil = occupiedUntil;
        this.visitId = visitId;
        this.sourceVisitId = sourceVisitId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    public UUID getId() { return id; }
    public UUID id() { return id; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID getServiceId() { return serviceId; }
    public UUID serviceId() { return serviceId; }
    public String getServiceName() { return serviceName; }
    public String serviceName() { return serviceName; }
    public BigDecimal getServicePrice() { return servicePrice; }
    public BigDecimal servicePrice() { return servicePrice; }
    public UUID getDoctorId() { return doctorId; }
    public UUID doctorId() { return doctorId; }
    public String getDoctorDisplayName() { return doctorDisplayName; }
    public String doctorDisplayName() { return doctorDisplayName; }
    public String getStatus() { return status; }
    public String status() { return status; }
    public String getBookingSource() { return bookingSource; }
    public String bookingSource() { return bookingSource; }
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
    public Instant getOccupiedUntil() { return occupiedUntil; }
    public Instant occupiedUntil() { return occupiedUntil; }
    public UUID getVisitId() { return visitId; }
    public UUID visitId() { return visitId; }
    public UUID getSourceVisitId() { return sourceVisitId; }
    public UUID sourceVisitId() { return sourceVisitId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant createdAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant updatedAt() { return updatedAt; }
}
