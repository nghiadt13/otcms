package fu.fa26.sep490.g58.otcms_be.patient.model;

import java.time.Instant;
import java.util.UUID;

public class CareRelationship {
    private final UUID id;
    private final UUID requesterAccountId;
    private final UUID dependentPatientProfileId;
    private final UUID confirmingAccountId;
    private final State state;
    private final Instant requestedAt;
    private final Instant confirmedAt;
    private final Instant revokedAt;
    private final UUID revokedByAccountId;
    private Relationship relationship;
    private VerificationMethod verificationMethod;
    private PendingReason pendingReason;
    private UUID verifiedByAccountId;
    private Instant verifiedAt;

    public enum Relationship { PARENT, CHILD, SPOUSE, SIBLING }
    public enum VerificationMethod { EMAIL_OTP, COUNTER_CHECK, CREATED_WITHOUT_OTP }
    public enum PendingReason { OTP, COUNTER }

    public CareRelationship(UUID id, UUID requester, UUID profile, UUID confirming, State state, Instant requested,
            Instant confirmed, Instant revoked, UUID revokedBy, Relationship relationship,
            VerificationMethod method, PendingReason pending, UUID verifiedBy, Instant verified) {
        this(id, requester, profile, confirming, state, requested, confirmed, revoked, revokedBy);
        this.relationship = relationship;
        this.verificationMethod = method;
        this.pendingReason = pending;
        this.verifiedByAccountId = verifiedBy;
        this.verifiedAt = verified;
    }
    public Relationship relationship() { return relationship; }
    public VerificationMethod verificationMethod() { return verificationMethod; }
    public PendingReason pendingReason() { return pendingReason; }
    public UUID verifiedByAccountId() { return verifiedByAccountId; }
    public Instant verifiedAt() { return verifiedAt; }
    public CareRelationship verified(VerificationMethod method, UUID actor, Instant now) {
        return new CareRelationship(id, requesterAccountId, dependentPatientProfileId, confirmingAccountId,
                State.CONFIRMED, requestedAt, now, null, null, relationship, method, null, actor, now);
    }


    public CareRelationship(UUID id, UUID requesterAccountId, UUID dependentPatientProfileId, UUID confirmingAccountId, State state, Instant requestedAt, Instant confirmedAt, Instant revokedAt, UUID revokedByAccountId) {
        this.id = id;
        this.requesterAccountId = requesterAccountId;
        this.dependentPatientProfileId = dependentPatientProfileId;
        this.confirmingAccountId = confirmingAccountId;
        this.state = state;
        this.requestedAt = requestedAt;
        this.confirmedAt = confirmedAt;
        this.revokedAt = revokedAt;
        this.revokedByAccountId = revokedByAccountId;
    }

    public UUID getId() {
        return id;
    }

    public UUID id() {
        return id;
    }

    public UUID getRequesterAccountId() {
        return requesterAccountId;
    }

    public UUID requesterAccountId() {
        return requesterAccountId;
    }

    public UUID getDependentPatientProfileId() {
        return dependentPatientProfileId;
    }

    public UUID dependentPatientProfileId() {
        return dependentPatientProfileId;
    }

    public UUID getConfirmingAccountId() {
        return confirmingAccountId;
    }

    public UUID confirmingAccountId() {
        return confirmingAccountId;
    }

    public State getState() {
        return state;
    }

    public State state() {
        return state;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public UUID getRevokedByAccountId() {
        return revokedByAccountId;
    }

    public UUID revokedByAccountId() {
        return revokedByAccountId;
    }

    public enum State {
        REQUESTED,
        CONFIRMED,
        REVOKED
    }

    public CareRelationship confirm(Instant now) {
        return new CareRelationship(id, requesterAccountId, dependentPatientProfileId, confirmingAccountId,
                State.CONFIRMED, requestedAt, now, null, null);
    }

    public CareRelationship revoke(UUID actor, Instant now) {
        return new CareRelationship(id, requesterAccountId, dependentPatientProfileId, confirmingAccountId,
                State.REVOKED, requestedAt, confirmedAt, now, actor, relationship, verificationMethod, null, verifiedByAccountId, verifiedAt);
    }
}
