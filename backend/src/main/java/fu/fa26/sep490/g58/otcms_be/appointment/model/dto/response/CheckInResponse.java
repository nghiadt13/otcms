package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response;


import java.time.Instant;
import java.util.UUID;

public class CheckInResponse {
    private final UUID appointmentId;
    private final UUID visitId;
    private final Instant checkedInAt;
    private final Integer queueNumber;
    private final String doctorName;
    private final String roomLabel;
    public CheckInResponse(UUID appointmentId, UUID visitId, Instant checkedInAt, Integer queueNumber,
            String doctorName, String roomLabel) {
        this.appointmentId = appointmentId;
        this.visitId = visitId;
        this.checkedInAt = checkedInAt;
        this.queueNumber = queueNumber;
        this.doctorName = doctorName;
        this.roomLabel = roomLabel;
    }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID appointmentId() { return appointmentId; }
    public UUID getVisitId() { return visitId; }
    public UUID visitId() { return visitId; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant checkedInAt() { return checkedInAt; }
    public Integer getQueueNumber() { return queueNumber; }
    public Integer queueNumber() { return queueNumber; }
    public String getDoctorName() { return doctorName; }
    public String doctorName() { return doctorName; }
    public String getRoomLabel() { return roomLabel; }
    public String roomLabel() { return roomLabel; }
}
