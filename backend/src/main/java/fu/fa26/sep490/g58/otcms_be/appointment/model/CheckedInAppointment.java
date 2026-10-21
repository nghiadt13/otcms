package fu.fa26.sep490.g58.otcms_be.appointment.model;


import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CheckedInAppointment {
    private final UUID appointmentId;
    private final UUID visitId;
    private final Instant checkedInAt;
    public CheckedInAppointment(UUID appointmentId, UUID visitId, Instant checkedInAt) {
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
    @Override public boolean equals(Object other) {
        return this == other || other instanceof CheckedInAppointment value
                && Objects.equals(appointmentId, value.appointmentId)
                && Objects.equals(visitId, value.visitId)
                && Objects.equals(checkedInAt, value.checkedInAt);
    }
    @Override public int hashCode() { return Objects.hash(appointmentId, visitId, checkedInAt); }
}
