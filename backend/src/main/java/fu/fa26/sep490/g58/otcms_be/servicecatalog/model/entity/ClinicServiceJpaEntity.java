package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_services")
public class ClinicServiceJpaEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 160) private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "duration_minutes", nullable = false) private int durationMinutes;
    @Column(name = "buffer_minutes", nullable = false) private int bufferMinutes;
    @Column(name = "online_bookable", nullable = false) private boolean onlineBookable;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false) private Instant updatedAt;

    protected ClinicServiceJpaEntity() { }

    public ClinicServiceJpaEntity(UUID id, String name, String description, int durationMinutes,
                           int bufferMinutes, boolean onlineBookable, boolean active) {
        this.id = id; this.name = name; this.description = description; this.durationMinutes = durationMinutes;
        this.bufferMinutes = bufferMinutes; this.onlineBookable = onlineBookable; this.active = active;
    }

    @PreUpdate void touch() { updatedAt = Instant.now(); }
    public UUID id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public int durationMinutes() { return durationMinutes; }
    public int bufferMinutes() { return bufferMinutes; }
    public boolean onlineBookable() { return onlineBookable; }
    public boolean active() { return active; }
    public void activate() { this.active = true; }
    public void deactivate() { this.active = false; }
}
