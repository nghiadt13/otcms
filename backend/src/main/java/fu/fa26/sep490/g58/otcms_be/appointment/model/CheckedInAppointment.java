package fu.fa26.sep490.g58.otcms_be.appointment.model;


import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueTicket;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class CheckedInAppointment {
    private final UUID appointmentId;
    private final UUID visitId;
    private final Instant checkedInAt;
    /** Queue number, doctor and room of the visit; null when the visit predates the exam queue. */
    private final ExamQueueTicket queueTicket;
    public CheckedInAppointment(UUID appointmentId, UUID visitId, Instant checkedInAt, ExamQueueTicket queueTicket) {
        this.appointmentId = appointmentId;
        this.visitId = visitId;
        this.checkedInAt = checkedInAt;
        this.queueTicket = queueTicket;
    }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID appointmentId() { return appointmentId; }
    public UUID getVisitId() { return visitId; }
    public UUID visitId() { return visitId; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant checkedInAt() { return checkedInAt; }
    public ExamQueueTicket getQueueTicket() { return queueTicket; }
    public ExamQueueTicket queueTicket() { return queueTicket; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof CheckedInAppointment value
                && Objects.equals(appointmentId, value.appointmentId)
                && Objects.equals(visitId, value.visitId)
                && Objects.equals(checkedInAt, value.checkedInAt)
                && Objects.equals(queueTicket, value.queueTicket);
    }
    @Override public int hashCode() { return Objects.hash(appointmentId, visitId, checkedInAt, queueTicket); }
}
