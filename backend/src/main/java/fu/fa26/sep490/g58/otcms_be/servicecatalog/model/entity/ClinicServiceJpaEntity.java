package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;

@Entity
@Table(name = "clinic_services")
public class ClinicServiceJpaEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 160) private String name;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(name = "service_type", nullable = false, length = 16)
    private fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType type;
    @Column(length = 16) private String modality;
    @Column(name = "patient_note", columnDefinition = "text") private String patientNote;
    @Convert(converter = LabIndicatorListConverter.class)
    @Column(name = "lab_indicators", columnDefinition = "text", nullable = false)
    private List<LabIndicator> indicators = List.of();
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Column(name = "duration_minutes", nullable = false) private int durationMinutes;
    @Column(name = "buffer_minutes", nullable = false) private int bufferMinutes;
    @Column(name = "online_bookable", nullable = false) private boolean onlineBookable;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false) private Instant updatedAt;

    protected ClinicServiceJpaEntity() { }

    public ClinicServiceJpaEntity(UUID id, String name, String description, BigDecimal price, int durationMinutes,
                           int bufferMinutes, boolean onlineBookable, boolean active) {
        this(id, name, description, fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType.EXAMINATION,
                price, durationMinutes, bufferMinutes, onlineBookable, active);
    }

    public ClinicServiceJpaEntity(UUID id, String name, String description,
                           fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType type,
                           BigDecimal price, int durationMinutes, int bufferMinutes, boolean onlineBookable, boolean active) {
        this.id = id; this.name = name; this.description = description; this.price = price;
        this.type = type;
        this.durationMinutes = durationMinutes; this.bufferMinutes = bufferMinutes;
        this.onlineBookable = onlineBookable; this.active = active;
    }

    public ClinicServiceJpaEntity(UUID id, String name, String description, int durationMinutes,
                           int bufferMinutes, boolean onlineBookable, boolean active) {
        this(id, name, description, BigDecimal.ZERO, durationMinutes, bufferMinutes, onlineBookable, active);
    }

    @PreUpdate void touch() { updatedAt = Instant.now(); }
    public UUID id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType type() { return type; }
    public String modality() { return modality; }
    public String patientNote() { return patientNote; }
    public List<LabIndicator> indicators() { return indicators == null ? List.of() : List.copyOf(indicators); }
    public BigDecimal price() { return price; }
    public int durationMinutes() { return durationMinutes; }
    public int bufferMinutes() { return bufferMinutes; }
    public boolean onlineBookable() { return onlineBookable; }
    public boolean active() { return active; }
    public void activate() { this.active = true; }
    public void deactivate() { this.active = false; }
    public void updateCatalogDetails(String modality, String patientNote, List<LabIndicator> indicators) {
        this.modality = modality;
        this.patientNote = patientNote;
        this.indicators = indicators == null ? List.of() : List.copyOf(indicators);
    }
}
