package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable Visit acknowledgement, not a copy of Diagnostic findings or reports. */
@Entity
@Table(name = "visit_imaging_result_receipts")
public class VisitImagingResultReceiptJpaEntity {
    @Id @Column(name = "event_id") private UUID eventId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "visit_id", nullable = false) private UUID visitId;
    @Column(name = "patient_profile_id", nullable = false) private UUID patientProfileId;
    @Column(name = "event_type", nullable = false, length = 32) private String eventType;
    @Column(name = "payload_sha256", nullable = false, length = 64) private String payloadSha256;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    protected VisitImagingResultReceiptJpaEntity() { }
    public UUID getEventId() { return eventId; }
    public UUID getRequestId() { return requestId; }
    public UUID getVisitId() { return visitId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public String getEventType() { return eventType; }
    public String getPayloadSha256() { return payloadSha256; }
}
