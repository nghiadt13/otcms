package fu.fa26.sep490.g58.otcms_be.servicecatalog.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.List;

public class ClinicService {
    private final UUID id;
    private final String name;
    private final String description;
    private final ClinicServiceType type;
    private final String modality;
    private final String patientNote;
    private final List<LabIndicator> indicators;
    private final BigDecimal price;
    private final Duration consultationDuration;
    private final Duration bufferDuration;
    private final boolean onlineBookable;
    private final boolean active;
    public ClinicService(UUID id, String name, String description, BigDecimal price, Duration consultationDuration,
                         Duration bufferDuration, boolean onlineBookable, boolean active) {
        this(id, name, description, ClinicServiceType.EXAMINATION, price, consultationDuration,
                bufferDuration, onlineBookable, active, null, null, List.of());
    }
    public ClinicService(UUID id, String name, String description, ClinicServiceType type, BigDecimal price,
                         Duration consultationDuration, Duration bufferDuration, boolean onlineBookable, boolean active) {
        this(id, name, description, type, price, consultationDuration, bufferDuration, onlineBookable, active,
                null, null, List.of());
    }
    public ClinicService(UUID id, String name, String description, ClinicServiceType type, BigDecimal price,
                         Duration consultationDuration, Duration bufferDuration, boolean onlineBookable, boolean active,
                         String modality, String patientNote, List<LabIndicator> indicators) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.modality = modality;
        this.patientNote = patientNote;
        this.indicators = indicators == null ? List.of() : List.copyOf(indicators);
        this.price = price;
        this.consultationDuration = consultationDuration;
        this.bufferDuration = bufferDuration;
        this.onlineBookable = onlineBookable;
        this.active = active;
    }
    public ClinicService(UUID id, String name, String description, Duration consultationDuration,
                         Duration bufferDuration, boolean onlineBookable, boolean active) {
        this(id, name, description, BigDecimal.ZERO, consultationDuration, bufferDuration, onlineBookable, active);
    }
    public UUID getId() { return id; }
    public UUID id() { return id; }
    public String getName() { return name; }
    public String name() { return name; }
    public String getDescription() { return description; }
    public String description() { return description; }
    public ClinicServiceType getType() { return type; }
    public ClinicServiceType type() { return type; }
    public String getModality() { return modality; }
    public String modality() { return modality; }
    public String getPatientNote() { return patientNote; }
    public String patientNote() { return patientNote; }
    public List<LabIndicator> getIndicators() { return indicators; }
    public List<LabIndicator> indicators() { return indicators; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal price() { return price; }
    public Duration getConsultationDuration() { return consultationDuration; }
    public Duration consultationDuration() { return consultationDuration; }
    public Duration getBufferDuration() { return bufferDuration; }
    public Duration bufferDuration() { return bufferDuration; }
    public boolean getOnlineBookable() { return onlineBookable; }
    public boolean isOnlineBookable() { return onlineBookable; }
    public boolean onlineBookable() { return onlineBookable; }
    public boolean getActive() { return active; }
    public boolean isActive() { return active; }
    public boolean active() { return active; }
}
