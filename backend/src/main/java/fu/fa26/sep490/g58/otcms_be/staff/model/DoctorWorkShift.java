package fu.fa26.sep490.g58.otcms_be.staff.model;

import java.time.Instant;
import java.util.UUID;

public class DoctorWorkShift {
    private final UUID id;
    private final UUID doctorProfileId;
    private final UUID roomId;
    private final Instant startsAt;
    private final Instant endsAt;
    public DoctorWorkShift(UUID id, UUID doctorProfileId, Instant startsAt, Instant endsAt) {
        this(id, doctorProfileId, startsAt, endsAt, null);
    }
    public DoctorWorkShift(UUID id, UUID doctorProfileId, Instant startsAt, Instant endsAt, UUID roomId) {
        this.id = id;
        this.doctorProfileId = doctorProfileId;
        this.roomId = roomId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
    public UUID getId() { return id; }
    public UUID id() { return id; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public UUID getRoomId() { return roomId; }
    public UUID roomId() { return roomId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant startsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant endsAt() { return endsAt; }
}
