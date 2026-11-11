package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "imaging_room_work_reservations")
public class ImagingRoomWorkReservationJpaEntity {
    @Id private UUID id;
    @Column(name = "request_id", nullable = false, unique = true) private UUID requestId;
    @Column(name = "room_id", nullable = false) private UUID roomId;
    @Column(name = "imaging_type", nullable = false) private String imagingType;
    @Column(nullable = false) private String state;
    @Column(name = "reserved_at", nullable = false, insertable = false, updatable = false) private Instant reservedAt;
    @Column(name = "released_at") private Instant releasedAt;
    @Column(name = "release_cause") private String releaseCause;

    protected ImagingRoomWorkReservationJpaEntity() { }

    public ImagingRoomWorkReservationJpaEntity(UUID id, UUID requestId, UUID roomId, String imagingType) {
        this.id = id;
        this.requestId = requestId;
        this.roomId = roomId;
        this.imagingType = imagingType;
        this.state = "RESERVED";
    }

    public void release(String cause, Instant now) {
        if (!"RESERVED".equals(state)) return;
        state = "RELEASED";
        releasedAt = now;
        releaseCause = cause;
    }

    public UUID getRequestId() { return requestId; }
    public UUID getRoomId() { return roomId; }
    public String getImagingType() { return imagingType; }
    public String getState() { return state; }
}
