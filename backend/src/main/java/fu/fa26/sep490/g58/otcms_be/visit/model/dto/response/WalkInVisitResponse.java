package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;

@Schema(name = "WalkInResponse")
public class WalkInVisitResponse {
    private final UUID visitId;
    private final UUID patientProfileId;
    private final UUID priorAppointmentId;
    private final Instant arrivedAt;
    private final VisitExamStatus examStatus;
    private final Integer queueNumber;
    private final String doctorName;
    private final String roomLabel;
    private final BigDecimal feeAmount;

    public WalkInVisitResponse(UUID visitId, UUID patientProfileId, UUID priorAppointmentId, Instant arrivedAt,
            VisitExamStatus examStatus, Integer queueNumber, String doctorName, String roomLabel,
            BigDecimal feeAmount) {
        this.visitId = visitId;
        this.patientProfileId = patientProfileId;
        this.priorAppointmentId = priorAppointmentId;
        this.arrivedAt = arrivedAt;
        this.examStatus = examStatus;
        this.queueNumber = queueNumber;
        this.doctorName = doctorName;
        this.roomLabel = roomLabel;
        this.feeAmount = feeAmount;
    }

    public UUID getVisitId() { return visitId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    @Schema(nullable = true)
    public UUID getPriorAppointmentId() { return priorAppointmentId; }
    public Instant getArrivedAt() { return arrivedAt; }
    public VisitExamStatus getExamStatus() { return examStatus; }
    /** Null while the walk-in waits for its fee. */
    @Schema(nullable = true)
    public Integer getQueueNumber() { return queueNumber; }
    @Schema(nullable = true)
    public String getDoctorName() { return doctorName; }
    /** Null while the walk-in waits for its fee: the room is taken from the doctor's shift at admission. */
    @Schema(nullable = true)
    public String getRoomLabel() { return roomLabel; }
    public BigDecimal getFeeAmount() { return feeAmount; }
}
