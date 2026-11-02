package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "walk_in_requests")
public class WalkInReceptionReplayJpaEntity {
    @Id
    private UUID id;

    @Column(name = "receptionist_account_id", nullable = false)
    private UUID receptionistAccountId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "visit_id", nullable = false, unique = true)
    private UUID visitId;

    protected WalkInReceptionReplayJpaEntity() { }

    public static WalkInReceptionReplayJpaEntity create(UUID receptionistAccountId, String idempotencyKey,
            String requestFingerprint, UUID visitId) {
        WalkInReceptionReplayJpaEntity entity = new WalkInReceptionReplayJpaEntity();
        entity.id = UUID.randomUUID();
        entity.receptionistAccountId = receptionistAccountId;
        entity.idempotencyKey = idempotencyKey;
        entity.requestFingerprint = requestFingerprint;
        entity.visitId = visitId;
        return entity;
    }

    public String getRequestFingerprint() { return requestFingerprint; }
    public UUID getVisitId() { return visitId; }
}
