package fu.fa26.sep490.g58.otcms_be.appointment.model;


import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable appointment creation facts persisted together with its creation audit. */
public class AppointmentBooking {
    private final UUID id;
    private final UUID patientProfileId;
    private final UUID serviceId;
    private final UUID doctorProfileId;
    private final BookingSource bookingSource;
    private final String purpose;
    private final Instant startTime;
    private final Instant endTime;
    private final int durationMinutes;
    private final int bufferMinutes;
    private final UUID createdByAccountId;
    private final ActorType actorType;
    private final Instant occurredAt;
    private final String correlationId;
    private final UUID sourceVisitId;
    private final UUID sourceAppointmentId;
    private final UUID orderingDoctorProfileId;
    private final String reason;
    public AppointmentBooking(UUID id, UUID patientProfileId, UUID serviceId, UUID doctorProfileId, BookingSource bookingSource, String purpose, Instant startTime, Instant endTime, int durationMinutes, int bufferMinutes, UUID createdByAccountId, ActorType actorType, Instant occurredAt, String correlationId) {
        this(id, patientProfileId, serviceId, doctorProfileId, bookingSource, purpose, startTime, endTime,
                durationMinutes, bufferMinutes, createdByAccountId, actorType, occurredAt, correlationId,
                null, null, null, null);
    }
    public AppointmentBooking(UUID id, UUID patientProfileId, UUID serviceId, UUID doctorProfileId,
            BookingSource bookingSource, String purpose, Instant startTime, Instant endTime, int durationMinutes,
            int bufferMinutes, UUID createdByAccountId, ActorType actorType, Instant occurredAt, String correlationId,
            UUID sourceVisitId, UUID sourceAppointmentId, UUID orderingDoctorProfileId, String reason) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(patientProfileId, "patientProfileId");
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(doctorProfileId, "doctorProfileId");
        Objects.requireNonNull(bookingSource, "bookingSource");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(createdByAccountId, "createdByAccountId");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
        purpose = purpose == null ? null : purpose.trim();
        if (purpose == null || purpose.isBlank() || purpose.length() > 1000 || durationMinutes < 1
                || bufferMinutes < 0 || !endTime.equals(startTime.plusSeconds(durationMinutes * 60L))
                || !startTime.isBefore(endTime) || correlationId.isBlank() || correlationId.length() > 128) {
            throw new IllegalArgumentException("Invalid appointment booking values");
        }
        if ((bookingSource == BookingSource.PATIENT_ONLINE && actorType != ActorType.PATIENT)
                || (bookingSource == BookingSource.RECEPTIONIST_ASSISTED && actorType != ActorType.RECEPTIONIST)
                || (bookingSource == BookingSource.DOCTOR_FOLLOW_UP && actorType != ActorType.DOCTOR)) {
            throw new IllegalArgumentException("Booking source must match the authenticated actor");
        }
        if ((bookingSource == BookingSource.DOCTOR_FOLLOW_UP)
                != (sourceVisitId != null && orderingDoctorProfileId != null && actorType == ActorType.DOCTOR)
                || (bookingSource != BookingSource.DOCTOR_FOLLOW_UP && (sourceVisitId != null
                || sourceAppointmentId != null || orderingDoctorProfileId != null || reason != null))
                || (reason != null && (reason.isBlank() || reason.trim().length() > 1000))) {
            throw new IllegalArgumentException("Invalid follow-up evidence");
        }
        this.id = id;
        this.patientProfileId = patientProfileId;
        this.serviceId = serviceId;
        this.doctorProfileId = doctorProfileId;
        this.bookingSource = bookingSource;
        this.purpose = purpose;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
        this.bufferMinutes = bufferMinutes;
        this.createdByAccountId = createdByAccountId;
        this.actorType = actorType;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.sourceVisitId = sourceVisitId;
        this.sourceAppointmentId = sourceAppointmentId;
        this.orderingDoctorProfileId = orderingDoctorProfileId;
        this.reason = reason == null ? null : reason.trim();
    }
    public UUID getId() { return id; }
    public UUID id() { return id; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID getServiceId() { return serviceId; }
    public UUID serviceId() { return serviceId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public BookingSource getBookingSource() { return bookingSource; }
    public BookingSource bookingSource() { return bookingSource; }
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
    public UUID getCreatedByAccountId() { return createdByAccountId; }
    public UUID createdByAccountId() { return createdByAccountId; }
    public ActorType getActorType() { return actorType; }
    public ActorType actorType() { return actorType; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant occurredAt() { return occurredAt; }
    public String getCorrelationId() { return correlationId; }
    public String correlationId() { return correlationId; }
    public UUID sourceVisitId() { return sourceVisitId; }
    public UUID sourceAppointmentId() { return sourceAppointmentId; }
    public UUID orderingDoctorProfileId() { return orderingDoctorProfileId; }
    public String reason() { return reason; }
    public enum BookingSource { PATIENT_ONLINE, RECEPTIONIST_ASSISTED, DOCTOR_FOLLOW_UP }
    public enum ActorType { PATIENT, RECEPTIONIST, DOCTOR }
}
