package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.time.Instant;
import java.util.UUID;

public class CheckInReplay {
    private final UUID visitId;
    private final Instant checkedInAt;
    public CheckInReplay(UUID visitId, Instant checkedInAt) {
        this.visitId = visitId;
        this.checkedInAt = checkedInAt;
    }
    public UUID getVisitId() { return visitId; }
    public UUID visitId() { return visitId; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant checkedInAt() { return checkedInAt; }
}
