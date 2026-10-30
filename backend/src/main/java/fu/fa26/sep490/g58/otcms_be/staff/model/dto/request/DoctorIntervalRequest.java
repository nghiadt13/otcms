package fu.fa26.sep490.g58.otcms_be.staff.model.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public class DoctorIntervalRequest {
    @NotNull private Instant startsAt;
    @NotNull private Instant endsAt;
    private UUID roomId;
    public DoctorIntervalRequest() { }
    public DoctorIntervalRequest(Instant startsAt, Instant endsAt) { this.startsAt = startsAt; this.endsAt = endsAt; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public UUID getRoomId() { return roomId; }
    public void setStartsAt(Instant value) { startsAt = value; }
    public void setEndsAt(Instant value) { endsAt = value; }
    public void setRoomId(UUID value) { roomId = value; }
}
