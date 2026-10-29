package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_arrivals")
public class AppointmentArrivalJpaEntity {
    @Id
    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "patient_profile_id", nullable = false)
    private UUID patientProfileId;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(nullable = false, length = 24)
    private String source;

    @Column(length = 500)
    private String reason;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected AppointmentArrivalJpaEntity() { }

    public UUID appointmentId() { return appointmentId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID actorAccountId() { return actorAccountId; }
    public String source() { return source; }
    public String reason() { return reason; }
    public Instant arrivedAt() { return arrivedAt; }
    public Instant recordedAt() { return recordedAt; }
}
