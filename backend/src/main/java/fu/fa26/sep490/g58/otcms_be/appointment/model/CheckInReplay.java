package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.time.Instant;
import java.util.UUID;

public class CheckInReplay {
    private final String requestFingerprint;
    private final UUID visitId;
    private final Instant checkedInAt;
    public CheckInReplay(String requestFingerprint, UUID visitId, Instant checkedInAt) {
        this.requestFingerprint = requestFingerprint;
        this.visitId = visitId;
        this.checkedInAt = checkedInAt;
    }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String requestFingerprint() { return requestFingerprint; }
    public UUID getVisitId() { return visitId; }
    public UUID visitId() { return visitId; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant checkedInAt() { return checkedInAt; }
}
