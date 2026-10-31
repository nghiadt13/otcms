package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctor_services")
public class DoctorServiceAssignmentJpaEntity {
    @Id private UUID id;
    @Column(name = "doctor_profile_id", nullable = false) private UUID doctorProfileId;
    @Column(name = "service_id", nullable = false) private UUID serviceId;
    @Column(nullable = false) private boolean active;
    @Column(name = "updated_at", nullable = false, insertable = false) private Instant updatedAt;
    protected DoctorServiceAssignmentJpaEntity() { }
    public DoctorServiceAssignmentJpaEntity(UUID id, UUID doctorProfileId, UUID serviceId, boolean active) {
        this.id=id; this.doctorProfileId=doctorProfileId; this.serviceId=serviceId; this.active=active;
    }
    public UUID id() { return id; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public UUID serviceId() { return serviceId; }
    public boolean active() { return active; }
    public void deactivate() { this.active=false; }
    @PreUpdate void touch() { this.updatedAt=Instant.now(); }
}
