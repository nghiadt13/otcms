package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "visit_check_ins")
public class VisitInitialReceptionJpaEntity {
    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false, unique = true)
    private UUID visitId;

    @Column(name = "receptionist_account_id", nullable = false)
    private UUID receptionistAccountId;

    @Column(name = "reception_type", nullable = false, length = 24)
    private String receptionType;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "prior_appointment_id")
    private UUID priorAppointmentId;

    protected VisitInitialReceptionJpaEntity() { }

    public static VisitInitialReceptionJpaEntity walkIn(UUID visitId, UUID receptionistAccountId,
            UUID priorAppointmentId, Instant arrivedAt) {
        VisitInitialReceptionJpaEntity entity = new VisitInitialReceptionJpaEntity();
        entity.id = UUID.randomUUID();
        entity.visitId = visitId;
        entity.receptionistAccountId = receptionistAccountId;
        entity.receptionType = "WALK_IN";
        entity.arrivedAt = arrivedAt;
        entity.checkedInAt = arrivedAt;
        entity.priorAppointmentId = priorAppointmentId;
        return entity;
    }
}
