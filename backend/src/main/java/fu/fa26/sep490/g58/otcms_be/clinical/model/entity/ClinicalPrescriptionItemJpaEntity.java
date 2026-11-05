package fu.fa26.sep490.g58.otcms_be.clinical.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "clinical_prescription_items", uniqueConstraints = @UniqueConstraint(name = "uk_clinical_prescription_line", columnNames = {"prescription_id", "line_number"}))
public class ClinicalPrescriptionItemJpaEntity {
    @Id private UUID id;
    @Column(name = "prescription_id", nullable = false) private UUID prescriptionId;
    @Column(name = "line_number", nullable = false) private int lineNumber;
    @Column(nullable = false, length = 255) private String medicine;
    @Column(nullable = false, length = 255) private String dose;
    @Column(nullable = false, length = 255) private String frequency;
    @Column(name = "usage", nullable = false, length = 2000) private String usage;

    protected ClinicalPrescriptionItemJpaEntity() { }
    public ClinicalPrescriptionItemJpaEntity(UUID prescriptionId, int lineNumber, String medicine, String dose,
            String frequency, String usage) {
        id = UUID.randomUUID(); this.prescriptionId = prescriptionId; this.lineNumber = lineNumber;
        this.medicine = medicine; this.dose = dose; this.frequency = frequency; this.usage = usage;
    }
    public UUID getId() { return id; }
    public UUID getPrescriptionId() { return prescriptionId; }
    public int getLineNumber() { return lineNumber; }
    public String getMedicine() { return medicine; }
    public String getDose() { return dose; }
    public String getFrequency() { return frequency; }
    public String getUsage() { return usage; }
}
