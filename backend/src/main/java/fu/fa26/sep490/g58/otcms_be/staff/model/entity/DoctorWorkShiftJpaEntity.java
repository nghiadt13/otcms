package fu.fa26.sep490.g58.otcms_be.staff.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctor_shifts")
public class DoctorWorkShiftJpaEntity {
    @Id private UUID id;
    @Column(name = "doctor_profile_id", nullable = false) private UUID doctorProfileId;
    @Column(name = "room_id") private UUID roomId;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    protected DoctorWorkShiftJpaEntity() { }
    public DoctorWorkShiftJpaEntity(UUID id, UUID doctorProfileId, Instant startsAt, Instant endsAt) {
        this(id, doctorProfileId, startsAt, endsAt, null);
    }
    public DoctorWorkShiftJpaEntity(UUID id, UUID doctorProfileId, Instant startsAt, Instant endsAt, UUID roomId) {
        this.id = id; this.doctorProfileId = doctorProfileId; this.startsAt = startsAt; this.endsAt = endsAt; this.roomId = roomId;
    }
    public void updateInterval(Instant startsAt, Instant endsAt) { updateInterval(startsAt, endsAt, roomId); }
    public void updateInterval(Instant startsAt, Instant endsAt, UUID roomId) { this.startsAt=startsAt; this.endsAt=endsAt; this.roomId=roomId; }
    public UUID id() { return id; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public UUID roomId() { return roomId; }
    public Instant startsAt() { return startsAt; }
    public Instant endsAt() { return endsAt; }
}
