package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class VisitRoomResponse {
    private final UUID id;
    private final String roomCode;
    private final String displayLabel;
    private final List<String> capabilities;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    public VisitRoomResponse(UUID id, String roomCode, String displayLabel, List<String> capabilities,
            boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.roomCode = roomCode;
        this.displayLabel = displayLabel;
        this.capabilities = List.copyOf(capabilities);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public String getDisplayLabel() { return displayLabel; }
    public List<String> getCapabilities() { return capabilities; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
