package fu.fa26.sep490.g58.otcms_be.appointment.model.entity;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicInsert;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_audits")
@DynamicInsert
public class AppointmentAuditJpaEntity {
    @Id
    private UUID id;
    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;
    @Column(name = "actor_account_id", nullable = false)
    private UUID actorAccountId;
    @Column(name = "actor_type", nullable = false, length = 16)
    private String actorType;
    @Column(nullable = false, length = 32)
    private String action;
    @Column(name = "new_status", nullable = false, length = 16)
    private String newStatus;
    @Column(name = "new_doctor_profile_id", nullable = false)
    private UUID newDoctorProfileId;
    @Column(name = "new_start_time", nullable = false)
    private Instant newStartTime;
    @Column(name = "new_end_time", nullable = false)
    private Instant newEndTime;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    protected AppointmentAuditJpaEntity() { }

    public static AppointmentAuditJpaEntity created(AppointmentBooking booking) {
        AppointmentAuditJpaEntity audit = new AppointmentAuditJpaEntity();
        audit.id = UUID.randomUUID();
        audit.appointmentId = booking.id();
        audit.actorAccountId = booking.createdByAccountId();
        audit.actorType = booking.actorType().name();
        audit.action = "CREATED";
        audit.newStatus = "CONFIRMED";
        audit.newDoctorProfileId = booking.doctorProfileId();
        audit.newStartTime = booking.startTime();
        audit.newEndTime = booking.endTime();
        audit.occurredAt = booking.occurredAt();
        audit.correlationId = booking.correlationId();
        return audit;
    }
}
