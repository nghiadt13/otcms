package fu.fa26.sep490.g58.otcms_be.visit.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Walk-in visit as created or replayed, with its exam-queue state (plans/261111-0208-visit-flow-r3, A4). */
public class CreatedWalkInVisit {
    private final UUID visitId;
    private final UUID patientProfileId;
    private final UUID priorAppointmentId;
    private final Instant arrivedAt;
    private final VisitExamStatus examStatus;
    private final Integer queueNumber;
    private final String doctorName;
    private final String roomLabel;
    private final BigDecimal feeAmount;

    public CreatedWalkInVisit(UUID visitId, UUID patientProfileId, UUID priorAppointmentId, Instant arrivedAt,
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

    public UUID visitId() { return visitId; }
    public UUID patientProfileId() { return patientProfileId; }
    public UUID priorAppointmentId() { return priorAppointmentId; }
    public Instant arrivedAt() { return arrivedAt; }
    public VisitExamStatus examStatus() { return examStatus; }
    public Integer queueNumber() { return queueNumber; }
    public String doctorName() { return doctorName; }
    public String roomLabel() { return roomLabel; }
    public BigDecimal feeAmount() { return feeAmount; }
}
