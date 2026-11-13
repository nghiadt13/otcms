package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of {@code queue_entries}: either the walk-in reception queue (stage RECEPTION) or the doctor's exam
 * queue (stage EXAM). Both queues share {@code status} values such as WAITING, so every query on this table must
 * filter by {@code stage} (see the two queue repositories). The stage never changes after insert.
 *
 * <p>Exam mutators only assign fields; legal moves are checked by VisitExamQueueServiceImpl through
 * {@link VisitExamStatus#canMoveTo(VisitExamStatus)}. Reception rows are closed only by
 * {@code WalkInReceptionQueueEntryJpaRepository.leaveReception} (a guarded native UPDATE).
 */
@Entity
@Table(name = "queue_entries")
public class QueueEntryJpaEntity {
    public static final String STAGE_RECEPTION = "RECEPTION";
    public static final String STAGE_EXAM = "EXAM";
    public static final String RECEPTION_WAITING = "WAITING";

    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false, updatable = false)
    private UUID visitId;

    @Column(nullable = false, length = 16, updatable = false)
    private String stage;

    @Column(nullable = false, length = 32)
    private String status;

    /** RECEPTION only: equals visits.arrived_at, orders the FIFO queue. */
    @Column(name = "arrived_at", updatable = false)
    private Instant arrivedAt;

    /** RECEPTION: when the row entered the queue. EXAM: when the row was created (old visit_exam_queue.created_at). */
    @Column(name = "entered_at", nullable = false, updatable = false)
    private Instant enteredAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "exit_reason", length = 24)
    private String exitReason;

    @Column(name = "doctor_profile_id")
    private UUID doctorProfileId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "clinic_date")
    private LocalDate clinicDate;

    @Column(name = "queue_number")
    private Integer queueNumber;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "awaiting_at")
    private Instant awaitingAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected QueueEntryJpaEntity() { }

    /** Walk-in reception queue: a new row always starts WAITING. */
    public static QueueEntryJpaEntity reception(UUID visitId, Instant arrivedAt, Instant enteredAt) {
        QueueEntryJpaEntity entity = new QueueEntryJpaEntity();
        entity.id = UUID.randomUUID();
        entity.visitId = visitId;
        entity.stage = STAGE_RECEPTION;
        entity.status = RECEPTION_WAITING;
        entity.arrivedAt = arrivedAt;
        entity.enteredAt = enteredAt;
        return entity;
    }

    /** Exam queue: walk-in visit created before payment; no queue number yet. */
    public static QueueEntryJpaEntity pendingPayment(UUID visitId, UUID doctorProfileId, UUID serviceId,
            LocalDate clinicDate, Instant createdAt) {
        QueueEntryJpaEntity entity = exam(visitId, doctorProfileId, serviceId, clinicDate, createdAt);
        entity.status = VisitExamStatus.PENDING_PAYMENT.name();
        return entity;
    }

    /** Exam queue: visit queued with its number at once (scheduled check-in or free walk-in). */
    public static QueueEntryJpaEntity waiting(UUID visitId, UUID doctorProfileId, UUID roomId, UUID serviceId,
            LocalDate clinicDate, int queueNumber, Instant at) {
        QueueEntryJpaEntity entity = exam(visitId, doctorProfileId, serviceId, clinicDate, at);
        entity.roomId = roomId;
        entity.queueNumber = queueNumber;
        entity.status = VisitExamStatus.WAITING.name();
        entity.queuedAt = at;
        return entity;
    }

    private static QueueEntryJpaEntity exam(UUID visitId, UUID doctorProfileId, UUID serviceId,
            LocalDate clinicDate, Instant createdAt) {
        QueueEntryJpaEntity entity = new QueueEntryJpaEntity();
        entity.id = UUID.randomUUID();
        entity.visitId = visitId;
        entity.stage = STAGE_EXAM;
        entity.doctorProfileId = doctorProfileId;
        entity.serviceId = serviceId;
        entity.clinicDate = clinicDate;
        entity.enteredAt = createdAt;
        return entity;
    }

    /** PENDING_PAYMENT to WAITING: the number belongs to the day the visit is admitted. */
    public void admit(int number, UUID admittedRoomId, LocalDate admittedOn, Instant at) {
        requireExam();
        this.queueNumber = number;
        this.roomId = admittedRoomId;
        this.clinicDate = admittedOn;
        this.status = VisitExamStatus.WAITING.name();
        this.queuedAt = at;
    }

    public void startExamination(Instant at) {
        requireExam();
        this.status = VisitExamStatus.IN_EXAMINATION.name();
        this.startedAt = at;
    }

    public void awaitConclusion(Instant at) {
        requireExam();
        this.status = VisitExamStatus.AWAITING_CONCLUSION.name();
        this.awaitingAt = at;
    }

    public void finish(Instant at) {
        requireExam();
        this.status = VisitExamStatus.COMPLETED.name();
        this.finishedAt = at;
    }

    public void cancel(Instant at) {
        requireExam();
        this.status = VisitExamStatus.CANCELLED.name();
        this.finishedAt = at;
    }

    private void requireExam() {
        if (!STAGE_EXAM.equals(stage)) throw new IllegalStateException("not an exam queue row: " + id);
    }

    public UUID getId() { return id; }
    public UUID getVisitId() { return visitId; }
    public String getStage() { return stage; }
    public String getStatus() { return status; }
    public Instant getArrivedAt() { return arrivedAt; }
    public Instant getEnteredAt() { return enteredAt; }
    public Instant getLeftAt() { return leftAt; }
    public String getExitReason() { return exitReason; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID getRoomId() { return roomId; }
    public UUID getServiceId() { return serviceId; }
    public LocalDate getClinicDate() { return clinicDate; }
    public Integer getQueueNumber() { return queueNumber; }
    public Instant getQueuedAt() { return queuedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getAwaitingAt() { return awaitingAt; }
    public Instant getFinishedAt() { return finishedAt; }

    /** Exam rows only: the status as the exam enum. */
    public VisitExamStatus getExamStatus() {
        requireExam();
        return VisitExamStatus.valueOf(status);
    }

    /** Exam rows only: creation time (name kept from the old visit_exam_queue entity). */
    public Instant getCreatedAt() {
        requireExam();
        return enteredAt;
    }
}
