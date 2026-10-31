package fu.fa26.sep490.g58.otcms_be.staff.model.dto.response;

import java.time.Instant;
import java.util.UUID;

public class DoctorIntervalResponse {
    private final UUID id;
    private final UUID doctorProfileId;
    private final Instant startsAt;
    private final Instant endsAt;
    private final UUID roomId;
    public DoctorIntervalResponse(UUID id, UUID doctorProfileId, Instant startsAt, Instant endsAt) {
        this(id, doctorProfileId, startsAt, endsAt, null);
    }
    public DoctorIntervalResponse(UUID id, UUID doctorProfileId, Instant startsAt, Instant endsAt, UUID roomId) {
        this.id = id; this.doctorProfileId = doctorProfileId; this.startsAt = startsAt; this.endsAt = endsAt; this.roomId = roomId;
    }
    public UUID getId() { return id; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public UUID getRoomId() { return roomId; }
}
