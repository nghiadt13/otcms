package fu.fa26.sep490.g58.otcms_be.clinical.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One ordering round of a visit (table clinical_ordering_rounds). Its id is the ordering_round_id carried by each order of the round. */
@Entity
@Table(name = "clinical_ordering_rounds")
public class ClinicalOrderingRoundJpaEntity {
    @Id private UUID id;
    @Column(name = "visit_id", nullable = false) private UUID visitId;
    @Column(name = "round_number", nullable = false) private int roundNumber;
    @Column(name = "ordered_by_doctor_id", nullable = false) private UUID orderedByDoctorId;
    @Column(name = "ordered_at", nullable = false) private Instant orderedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ClinicalOrderingRoundJpaEntity() { }

    public ClinicalOrderingRoundJpaEntity(UUID id, UUID visitId, int roundNumber, UUID orderedByDoctorId, Instant now) {
        this.id = id; this.visitId = visitId; this.roundNumber = roundNumber; this.orderedByDoctorId = orderedByDoctorId;
        this.orderedAt = now; this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getVisitId() { return visitId; }
    public int getRoundNumber() { return roundNumber; }
    public UUID getOrderedByDoctorId() { return orderedByDoctorId; }
    public Instant getOrderedAt() { return orderedAt; }
}
