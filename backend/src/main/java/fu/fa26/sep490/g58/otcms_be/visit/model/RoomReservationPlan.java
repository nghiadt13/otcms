package fu.fa26.sep490.g58.otcms_be.visit.model;

import java.time.Instant;
import java.util.UUID;

public class RoomReservationPlan {
    private final UUID roomId;
    private final String roomCode;
    private final String stepCode;
    private final String capabilityCode;
    private final Instant startsAt;
    private final Instant expectedEndsAt;
    private final Instant reservedUntil;
    private final int cleanupBufferMinutes;

    public RoomReservationPlan(UUID roomId, String roomCode, String stepCode, String capabilityCode,
            Instant startsAt, Instant expectedEndsAt, Instant reservedUntil, int cleanupBufferMinutes) {
        this.roomId = roomId;
        this.roomCode = roomCode;
        this.stepCode = stepCode;
        this.capabilityCode = capabilityCode;
        this.startsAt = startsAt;
        this.expectedEndsAt = expectedEndsAt;
        this.reservedUntil = reservedUntil;
        this.cleanupBufferMinutes = cleanupBufferMinutes;
    }

    public UUID roomId() { return roomId; }
    public String roomCode() { return roomCode; }
    public String stepCode() { return stepCode; }
    public String capabilityCode() { return capabilityCode; }
    public Instant startsAt() { return startsAt; }
    public Instant expectedEndsAt() { return expectedEndsAt; }
    public Instant reservedUntil() { return reservedUntil; }
    public int cleanupBufferMinutes() { return cleanupBufferMinutes; }
}
