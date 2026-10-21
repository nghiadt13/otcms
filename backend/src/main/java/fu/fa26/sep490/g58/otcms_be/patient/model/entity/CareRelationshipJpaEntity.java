package fu.fa26.sep490.g58.otcms_be.patient.model.entity;

import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.State;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_care_relationships")
public class CareRelationshipJpaEntity {
    @Id private UUID id;
    @Column(name = "requester_account_id", nullable = false) private UUID requesterAccountId;
    @Column(name = "dependent_patient_profile_id", nullable = false) private UUID dependentPatientProfileId;
    @Column(name = "confirming_account_id", nullable = false) private UUID confirmingAccountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private CareRelationship.State state;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoked_by_account_id") private UUID revokedByAccountId;

    protected CareRelationshipJpaEntity() { }

    public static CareRelationshipJpaEntity from(CareRelationship model) {
        var entity = new CareRelationshipJpaEntity();
        entity.id = model.id(); entity.requesterAccountId = model.requesterAccountId();
        entity.dependentPatientProfileId = model.dependentPatientProfileId();
        entity.confirmingAccountId = model.confirmingAccountId(); entity.state = model.state();
        entity.requestedAt = model.requestedAt(); entity.confirmedAt = model.confirmedAt();
        entity.revokedAt = model.revokedAt(); entity.revokedByAccountId = model.revokedByAccountId();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getRequesterAccountId() { return requesterAccountId; }
    public UUID getDependentPatientProfileId() { return dependentPatientProfileId; }
    public UUID getConfirmingAccountId() { return confirmingAccountId; }
    public CareRelationship.State getState() { return state; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public UUID getRevokedByAccountId() { return revokedByAccountId; }
}
