package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per visit in the doctor's exam queue. Mutators only assign fields; legal moves are
 * checked by VisitExamQueueServiceImpl through {@link VisitExamStatus#canMoveTo(VisitExamStatus)}.
 */
@Entity
@Table(name = "visit_exam_queue")
public class VisitExamQueueJpaEntity {
    @Id
    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "doctor_profile_id", nullable = false)
    private UUID doctorProfileId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "clinic_date", nullable = false)
    private LocalDate clinicDate;

    @Column(name = "queue_number")
    private Integer queueNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_status", nullable = false, length = 32)
    private VisitExamStatus examStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "awaiting_at")
    private Instant awaitingAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected VisitExamQueueJpaEntity() { }

    /** Walk-in visit created before payment; no queue number yet. */
    public static VisitExamQueueJpaEntity pendingPayment(UUID visitId, UUID doctorProfileId, UUID serviceId,
            LocalDate clinicDate, Instant createdAt) {
        VisitExamQueueJpaEntity entity = new VisitExamQueueJpaEntity();
        entity.visitId = visitId;
        entity.doctorProfileId = doctorProfileId;
        entity.serviceId = serviceId;
        entity.clinicDate = clinicDate;
        entity.examStatus = VisitExamStatus.PENDING_PAYMENT;
        entity.createdAt = createdAt;
        return entity;
    }

    /** Visit queued with its number at once (scheduled check-in or free walk-in). */
    public static VisitExamQueueJpaEntity waiting(UUID visitId, UUID doctorProfileId, UUID roomId, UUID serviceId,
            LocalDate clinicDate, int queueNumber, Instant at) {
        VisitExamQueueJpaEntity entity = new VisitExamQueueJpaEntity();
        entity.visitId = visitId;
        entity.doctorProfileId = doctorProfileId;
        entity.roomId = roomId;
        entity.serviceId = serviceId;
        entity.clinicDate = clinicDate;
        entity.queueNumber = queueNumber;
        entity.examStatus = VisitExamStatus.WAITING;
        entity.createdAt = at;
        entity.queuedAt = at;
        return entity;
    }

    /** PENDING_PAYMENT to WAITING: the number belongs to the day the visit is admitted. */
    public void admit(int number, UUID admittedRoomId, LocalDate admittedOn, Instant at) {
        this.queueNumber = number;
        this.roomId = admittedRoomId;
        this.clinicDate = admittedOn;
        this.examStatus = VisitExamStatus.WAITING;
        this.queuedAt = at;
    }

    public void startExamination(Instant at) {
        this.examStatus = VisitExamStatus.IN_EXAMINATION;
        this.startedAt = at;
    }

    public void awaitConclusion(Instant at) {
        this.examStatus = VisitExamStatus.AWAITING_CONCLUSION;
        this.awaitingAt = at;
    }

    public void finish(Instant at) {
        this.examStatus = VisitExamStatus.COMPLETED;
        this.finishedAt = at;
    }

    public void cancel(Instant at) {
        this.examStatus = VisitExamStatus.CANCELLED;
        this.finishedAt = at;
    }

    public UUID getVisitId() { return visitId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID getRoomId() { return roomId; }
    public UUID getServiceId() { return serviceId; }
    public LocalDate getClinicDate() { return clinicDate; }
    public Integer getQueueNumber() { return queueNumber; }
    public VisitExamStatus getExamStatus() { return examStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getQueuedAt() { return queuedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getAwaitingAt() { return awaitingAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
