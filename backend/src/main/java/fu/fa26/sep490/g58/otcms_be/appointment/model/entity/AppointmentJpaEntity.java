package fu.fa26.sep490.g58.otcms_be.appointment.model.entity;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.DynamicInsert;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@DynamicInsert
public class AppointmentJpaEntity {
    @Id
    private UUID id;
    @Column(name = "patient_profile_id", nullable = false)
    private UUID patientProfileId;
    @Column(name = "service_id", nullable = false)
    private UUID serviceId;
    @Column(name = "doctor_profile_id", nullable = false)
    private UUID doctorProfileId;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "booking_source", nullable = false, length = 24)
    private String bookingSource;
    @Column(nullable = false, length = 1000)
    private String purpose;
    @Column(name = "start_time", nullable = false)
    private Instant startTime;
    @Column(name = "end_time", nullable = false)
    private Instant endTime;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes;
    @Column(name = "occupied_until", insertable = false, updatable = false)
    private Instant occupiedUntil;
    @Column(name = "visit_id")
    private UUID visitId;
    @Column(name = "source_visit_id")
    private UUID sourceVisitId;
    @Column(name = "source_appointment_id")
    private UUID sourceAppointmentId;
    @Column(name = "ordering_doctor_profile_id")
    private UUID orderingDoctorProfileId;
    @Column(name = "created_by_account_id", nullable = false)
    private UUID createdByAccountId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected AppointmentJpaEntity() { }

    public static AppointmentJpaEntity confirmed(AppointmentBooking booking) {
        AppointmentJpaEntity entity = new AppointmentJpaEntity();
        entity.id = booking.id();
        entity.patientProfileId = booking.patientProfileId();
        entity.serviceId = booking.serviceId();
        entity.doctorProfileId = booking.doctorProfileId();
        entity.status = "CONFIRMED";
        entity.bookingSource = booking.bookingSource().name();
        entity.purpose = booking.purpose();
        entity.startTime = booking.startTime();
        entity.endTime = booking.endTime();
        entity.durationMinutes = booking.durationMinutes();
        entity.bufferMinutes = booking.bufferMinutes();
        entity.createdByAccountId = booking.createdByAccountId();
        entity.sourceVisitId = booking.sourceVisitId();
        entity.sourceAppointmentId = booking.sourceAppointmentId();
        entity.orderingDoctorProfileId = booking.orderingDoctorProfileId();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public String getStatus() { return status; }
    public String getBookingSource() { return bookingSource; }
    public String getPurpose() { return purpose; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getBufferMinutes() { return bufferMinutes; }
    public Instant getOccupiedUntil() { return occupiedUntil; }
    public UUID getVisitId() { return visitId; }
    public UUID getSourceVisitId() { return sourceVisitId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
