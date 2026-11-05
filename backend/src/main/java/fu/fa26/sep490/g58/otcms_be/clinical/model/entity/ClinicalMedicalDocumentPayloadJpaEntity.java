package fu.fa26.sep490.g58.otcms_be.clinical.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "clinical_medical_document_payloads")
public class ClinicalMedicalDocumentPayloadJpaEntity {
    @Id @Column(name = "document_id") private UUID documentId;
    @Column(nullable = false, columnDefinition = "bytea") private byte[] content;
    protected ClinicalMedicalDocumentPayloadJpaEntity() { }
    public byte[] getContent() { return content == null ? null : content.clone(); }
}
