package fu.fa26.sep490.g58.otcms_be.billing.model.entity;

import fu.fa26.sep490.g58.otcms_be.billing.model.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class InvoiceJpaEntity {
    @Id
    private UUID id;
    @Column(name = "invoice_number", nullable = false, unique = true, length = 48)
    private String invoiceNumber;
    @Column(name = "patient_profile_id", nullable = false)
    private UUID patientProfileId;
    @Column(name = "visit_id")
    private UUID visitId;
    @Column(name = "appointment_id")
    private UUID appointmentId;
    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;
    @Column(name = "source_fingerprint", nullable = false, length = 64)
    private String sourceFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvoiceStatus status;
    @Column(name = "created_by_account_id", nullable = false)
    private UUID createdByAccountId;
    @Column(name = "issued_at", nullable = false, insertable = false, updatable = false)
    private Instant issuedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected InvoiceJpaEntity() { }

    public static InvoiceJpaEntity visit(UUID id, String number, UUID patientId, UUID visitId,
            UUID actorId, String sourceFingerprint) {
        return create(id, number, patientId, visitId, null, "VISIT", actorId, sourceFingerprint);
    }

    public static InvoiceJpaEntity appointment(UUID id, String number, UUID patientId, UUID appointmentId,
            UUID actorId, String sourceFingerprint) {
        return create(id, number, patientId, null, appointmentId, "APPOINTMENT", actorId, sourceFingerprint);
    }

    private static InvoiceJpaEntity create(UUID id, String number, UUID patientId, UUID visitId,
            UUID appointmentId, String sourceType, UUID actorId, String sourceFingerprint) {
        InvoiceJpaEntity entity = new InvoiceJpaEntity();
        entity.id = id;
        entity.invoiceNumber = number;
        entity.patientProfileId = patientId;
        entity.visitId = visitId;
        entity.appointmentId = appointmentId;
        entity.sourceType = sourceType;
        entity.sourceFingerprint = sourceFingerprint;
        entity.status = InvoiceStatus.OPEN;
        entity.createdByAccountId = actorId;
        return entity;
    }

    public UUID getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID getVisitId() { return visitId; }
    public UUID getAppointmentId() { return appointmentId; }
    public String getSourceType() { return sourceType; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public UUID getCreatedByAccountId() { return createdByAccountId; }
    public Instant getIssuedAt() { return issuedAt; }
}
