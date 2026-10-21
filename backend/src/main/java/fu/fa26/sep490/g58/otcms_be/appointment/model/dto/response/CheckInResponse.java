package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response;


import java.time.Instant;
import java.util.UUID;

public class CheckInResponse {
    private final UUID appointmentId;
    private final UUID visitId;
    private final Instant checkedInAt;
    public CheckInResponse(UUID appointmentId, UUID visitId, Instant checkedInAt) {
        this.appointmentId = appointmentId;
        this.visitId = visitId;
        this.checkedInAt = checkedInAt;
    }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID appointmentId() { return appointmentId; }
    public UUID getVisitId() { return visitId; }
    public UUID visitId() { return visitId; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant checkedInAt() { return checkedInAt; }
}
