package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueTicket;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitExamQueueJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exam-queue state machine. Callers acquire the coordination guard before calling write methods
 * (MANDATORY transaction), which is what makes the max+1 queue number safe.
 */
@Service
public class VisitExamQueueServiceImpl implements VisitExamQueueService {
    private static final String STATE_CONFLICT = "VISIT_EXAM_STATE_CONFLICT";
    private final VisitExamQueueJpaRepository queue;
    private final DoctorScheduleService schedules;
    private final DoctorProfileIdentityQueryService doctorProfiles;
    private final VisitRoomJpaRepository rooms;
    private final AppointmentBookingProperties booking;

    public VisitExamQueueServiceImpl(VisitExamQueueJpaRepository queue, DoctorScheduleService schedules,
            DoctorProfileIdentityQueryService doctorProfiles, VisitRoomJpaRepository rooms,
            AppointmentBookingProperties booking) {
        this.queue = queue;
        this.schedules = schedules;
        this.doctorProfiles = doctorProfiles;
        this.rooms = rooms;
        this.booking = booking;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ExamQueueTicket enqueueWaiting(UUID visitId, UUID doctorProfileId, UUID serviceIdOrNull, Instant now) {
        QueueEntryJpaEntity existing = queue.findExamByVisitId(visitId).orElse(null);
        if (existing != null) {
            // Replay of the same check-in returns the existing number; any other state is a conflict.
            if (existing.getExamStatus() != VisitExamStatus.WAITING
                    || !existing.getDoctorProfileId().equals(doctorProfileId)) {
                throw conflict();
            }
            return ticket(existing);
        }
        LocalDate clinicDate = clinicDate(now);
        QueueEntryJpaEntity row = QueueEntryJpaEntity.waiting(visitId, doctorProfileId,
                currentRoomId(doctorProfileId, now), serviceIdOrNull, clinicDate,
                nextQueueNumber(doctorProfileId, clinicDate), now);
        queue.save(row);
        return ticket(row);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void createPendingPayment(UUID visitId, UUID doctorProfileId, UUID serviceId, Instant now) {
        if (queue.existsExamByVisitId(visitId)) {
            throw conflict();
        }
        queue.save(QueueEntryJpaEntity.pendingPayment(visitId, doctorProfileId, serviceId, clinicDate(now), now));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ExamQueueTicket admitAfterPayment(UUID visitId, Instant now) {
        QueueEntryJpaEntity row = requireRow(visitId);
        if (row.getExamStatus() == VisitExamStatus.WAITING) {
            return ticket(row);
        }
        requireMove(row, VisitExamStatus.WAITING);
        LocalDate clinicDate = clinicDate(now);
        row.admit(nextQueueNumber(row.getDoctorProfileId(), clinicDate), currentRoomId(row.getDoctorProfileId(), now),
                clinicDate, now);
        return ticket(row);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void startExamination(UUID visitId, UUID doctorProfileId, Instant now) {
        QueueEntryJpaEntity row = requireRow(visitId);
        if (!row.getDoctorProfileId().equals(doctorProfileId)) {
            throw conflict();
        }
        requireMove(row, VisitExamStatus.IN_EXAMINATION);
        if (queue.existsOtherExamRow(doctorProfileId,
                VisitExamStatus.IN_EXAMINATION.name(), visitId)) {
            throw conflict();
        }
        row.startExamination(now);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markAwaitingConclusion(UUID visitId, Instant now) {
        QueueEntryJpaEntity row = queue.findExamByVisitId(visitId).orElse(null);
        if (row == null || row.getExamStatus() == VisitExamStatus.AWAITING_CONCLUSION) {
            return;
        }
        requireMove(row, VisitExamStatus.AWAITING_CONCLUSION);
        row.awaitConclusion(now);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(UUID visitId, UUID doctorProfileId, Instant now) {
        QueueEntryJpaEntity row = requireRow(visitId);
        if (!row.getDoctorProfileId().equals(doctorProfileId)) {
            throw conflict();
        }
        requireMove(row, VisitExamStatus.COMPLETED);
        row.finish(now);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(UUID visitId, Instant now) {
        QueueEntryJpaEntity row = queue.findExamByVisitId(visitId).orElse(null);
        if (row == null) {
            return;
        }
        requireMove(row, VisitExamStatus.CANCELLED);
        row.cancel(now);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExamQueueEntry> find(UUID visitId) {
        return queue.findExamByVisitId(visitId).map(VisitExamQueueServiceImpl::entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> requiredFeeServiceId(UUID visitId) {
        return queue.findExamByVisitId(visitId)
                .filter(row -> row.getExamStatus() == VisitExamStatus.PENDING_PAYMENT)
                .map(QueueEntryJpaEntity::getServiceId);
    }

    private QueueEntryJpaEntity requireRow(UUID visitId) {
        return queue.findExamByVisitId(visitId).orElseThrow(VisitExamQueueServiceImpl::conflict);
    }

    private static void requireMove(QueueEntryJpaEntity row, VisitExamStatus next) {
        if (!row.getExamStatus().canMoveTo(next)) {
            throw conflict();
        }
    }

    private static VisitOperationException conflict() {
        return new VisitOperationException(STATE_CONFLICT);
    }

    private LocalDate clinicDate(Instant now) {
        return now.atZone(booking.clinicTimeZone()).toLocalDate();
    }

    private int nextQueueNumber(UUID doctorProfileId, LocalDate clinicDate) {
        return queue.findMaxQueueNumber(doctorProfileId, clinicDate) + 1;
    }

    /** Room of the doctor's shift covering this instant; null when that shift has no room. */
    private UUID currentRoomId(UUID doctorProfileId, Instant now) {
        return schedules.forProfile(doctorProfileId, now, now.plusSeconds(1)).stream()
                .min(Comparator.comparing(DoctorWorkShift::startsAt))
                .map(DoctorWorkShift::roomId)
                .orElse(null);
    }

    private ExamQueueTicket ticket(QueueEntryJpaEntity row) {
        String doctorName = doctorProfiles.findDoctorNames(List.of(row.getDoctorProfileId()))
                .get(row.getDoctorProfileId());
        String roomLabel = row.getRoomId() == null ? null
                : rooms.findById(row.getRoomId()).map(VisitRoomJpaEntity::getDisplayLabel).orElse(null);
        return new ExamQueueTicket(row.getQueueNumber(), row.getDoctorProfileId(), doctorName, row.getRoomId(),
                roomLabel);
    }

    private static ExamQueueEntry entry(QueueEntryJpaEntity row) {
        return new ExamQueueEntry(row.getVisitId(), row.getDoctorProfileId(), row.getRoomId(), row.getServiceId(),
                row.getQueueNumber(), row.getExamStatus(), row.getClinicDate(), row.getQueuedAt(),
                row.getStartedAt(), row.getAwaitingAt(), row.getFinishedAt());
    }
}
