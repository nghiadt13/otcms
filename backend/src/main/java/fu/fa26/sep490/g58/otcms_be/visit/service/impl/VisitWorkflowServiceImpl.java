package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.exception.ReceptionWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.service.ReceptionWorkflowService.CheckInCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ReceptionWorkflowService.CheckInResult;
import fu.fa26.sep490.g58.otcms_be.visit.service.ReceptionWorkflowService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitWorkflowService.FollowUpEvidence;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitScheduledCareService.ScheduledCareCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitScheduledCareService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitWorkflowService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitEvidenceException;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.ScheduledCarePersistenceData;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInVisitLifecycleService;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitWorkflowRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitWorkflowRepository.CheckInData;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueTicket;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitWorkflowServiceImpl implements VisitWorkflowService, ReceptionWorkflowService, VisitScheduledCareService {
    /** Shortest care interval written for a scheduled examination; the interval is an estimate, not a booking. */
    private static final Duration MINIMUM_CARE_INTERVAL = Duration.ofMinutes(30);
    private final VisitWorkflowRepository workflowRepository;
    private final Clock clock;
    private final WalkInVisitLifecycleService walkInLifecycle;
    private final fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalFollowUpEvidenceQueryService clinical;
    private final VisitExamQueueService examQueue;
    private final DoctorProfileIdentityQueryService doctorProfiles;
    private final VisitRoomJpaRepository visitRooms;
    private final VisitAuditRecorder audit;

    public VisitWorkflowServiceImpl(VisitWorkflowRepository workflowRepository, Clock clock,
            WalkInVisitLifecycleService walkInLifecycle,
            fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalFollowUpEvidenceQueryService clinical,
            VisitExamQueueService examQueue, DoctorProfileIdentityQueryService doctorProfiles,
            VisitRoomJpaRepository visitRooms, VisitAuditRecorder audit) {
        this.workflowRepository = workflowRepository; this.clock = clock; this.walkInLifecycle = walkInLifecycle;
        this.clinical = clinical; this.examQueue = examQueue; this.doctorProfiles = doctorProfiles;
        this.visitRooms = visitRooms;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpEvidence requireFollowUpEvidence(UUID visitId, UUID authenticatedDoctorProfileId) {
        VisitWorkflowRepository.FollowUpEvidenceRow row = workflowRepository.findFollowUpEvidence(visitId, authenticatedDoctorProfileId);
        if (row == null) throw new VisitEvidenceException("SOURCE_VISIT_NOT_FOUND");
        if (!row.assigned()) throw new VisitEvidenceException("SOURCE_VISIT_NOT_FOUND");
        var examination = clinical.getEvidence(visitId);
        return new FollowUpEvidence(visitId, row.patientProfileId(), authenticatedDoctorProfileId,
                row.assigned(), examination.examinationCompleted(), examination.followUpIndicated(), row.originatingAppointmentId(), clock.instant());
    }

    /**
     * Starts the examination of a checked-in appointment. The appointment's time window and the doctor's overlapping
     * care are not checked: the queue allows one IN_EXAMINATION visit per doctor (VISIT_EXAM_STATE_CONFLICT otherwise).
     */
    @Override
    @Transactional(noRollbackFor = VisitWorkflowException.class)
    public void startScheduledCare(ScheduledCareCommand command) {
        validateScheduledCare(command);
        if (!workflowRepository.lockScheduledVisit(command.visitId(), command.appointmentId(), command.patientProfileId()))
            throw new VisitWorkflowException("VISIT_LINK_CONFLICT");
        Instant now = command.occurredAt();
        if (workflowRepository.hasActiveCare(command.visitId())) throw new VisitWorkflowException("VISIT_CARE_NOT_STARTABLE");
        try {
            examQueue.startExamination(command.visitId(), command.doctorProfileId(), now);
        } catch (VisitOperationException failure) {
            throw new VisitWorkflowException(failure.code(), failure);
        }
        Instant expectedEnd = careEnd(command.scheduledEnd(), now);
        UUID assignmentId = UUID.randomUUID();
        workflowRepository.insertScheduledCare(assignmentId, persistenceData(command), expectedEnd, now);
        appendCareAudit(command, "STARTED", "SCHEDULED_APPOINTMENT", null,
                Timestamp.from(expectedEnd), now, assignmentId);
    }

    /** Scheduled completion closes the visit through the shared doctor path, in the appointment's transaction. */
    @Override
    @Transactional
    public void completeScheduledCare(ScheduledCareCommand command) {
        validateScheduledCare(command);
        if (!workflowRepository.lockScheduledVisit(command.visitId(), command.appointmentId(), command.patientProfileId()))
            throw new VisitWorkflowException("VISIT_LINK_CONFLICT");
        try {
            walkInLifecycle.completeByDoctor(command.visitId(), command.doctorProfileId(), command.actorAccountId(),
                    command.correlationId());
        } catch (VisitOperationException failure) {
            throw new VisitWorkflowException(failure.code(), failure);
        }
    }

    @Override
    @Transactional
    public void cancelCheckedInVisit(ScheduledCareCommand command) {
        validateScheduledCare(command);
        if (!workflowRepository.lockScheduledVisit(command.visitId(), command.appointmentId(), command.patientProfileId()))
            throw new VisitWorkflowException("VISIT_LINK_CONFLICT");
        if (clinical.getEvidence(command.visitId()).examinationExists()) throw new VisitWorkflowException("VISIT_CANCELLATION_NOT_ALLOWED");
        if (!workflowRepository.closeCancellableVisit(persistenceData(command))) throw new VisitWorkflowException("VISIT_CANCELLATION_NOT_ALLOWED");
        workflowRepository.releaseReservedCare(command.visitId(), command.occurredAt());
        try {
            examQueue.cancel(command.visitId(), command.occurredAt());
        } catch (VisitOperationException failure) {
            throw new VisitWorkflowException(failure.code(), failure);
        }
    }

    /** End of the care interval: the scheduled end, or {@link #MINIMUM_CARE_INTERVAL} from start if that has passed. */
    private static Instant careEnd(Instant scheduledEnd, Instant now) {
        Instant minimum = now.plus(MINIMUM_CARE_INTERVAL);
        return scheduledEnd.isAfter(minimum) ? scheduledEnd : minimum;
    }

    private void validateScheduledCare(ScheduledCareCommand command) {
        if (command == null || command.appointmentId() == null || command.visitId() == null
                || command.patientProfileId() == null || command.doctorProfileId() == null
                || command.scheduledEnd() == null || command.occurredAt() == null
                || command.actorAccountId() == null || command.expectedDurationMinutes() < 1
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128) {
            throw new VisitWorkflowException("VALIDATION_FAILED");
        }
    }

    private ScheduledCarePersistenceData persistenceData(ScheduledCareCommand command) {
        return new ScheduledCarePersistenceData(command.appointmentId(), command.visitId(),
                command.patientProfileId(), command.doctorProfileId(), command.scheduledEnd(),
                command.occurredAt(), command.actorAccountId(), command.expectedDurationMinutes(),
                command.correlationId());
    }

    private void appendCareAudit(ScheduledCareCommand command, String action, String stepCode,
            Timestamp oldEnd, Timestamp newEnd, Instant occurredAt) {
        appendCareAudit(command, action, stepCode, oldEnd, newEnd, occurredAt, null);
    }

    private void appendCareAudit(ScheduledCareCommand command, String action, String stepCode,
            Timestamp oldEnd, Timestamp newEnd, Instant occurredAt, UUID assignmentId) {
        if (command.actorAccountId() == null || command.correlationId() == null
                || command.correlationId().isBlank() || command.expectedDurationMinutes() < 1) {
            throw new VisitWorkflowException("VALIDATION_FAILED");
        }
        UUID careAssignmentId = assignmentId == null
                ? workflowRepository.findActiveAssignment(command.visitId(), command.doctorProfileId()) : assignmentId;
        audit.careAssignment(command.visitId(), careAssignmentId, command.actorAccountId(), action,
                stepCode == null ? "SCHEDULED_APPOINTMENT" : stepCode,
                oldEnd == null ? null : oldEnd.toInstant(), newEnd == null ? null : newEnd.toInstant(), occurredAt,
                command.correlationId());
    }

    /** @ears REQ-RECEPTION-003 REQ-RECEPTION-010 AC25 AC31 AC32 */
    @Override
    @Transactional(noRollbackFor = ReceptionWorkflowException.class)
    public CheckInResult checkInAtomically(CheckInCommand command) {
        if (command == null || command.actorAccountId() == null || command.appointmentId() == null
                || command.patientProfileId() == null || command.doctorProfileId() == null
                || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.idempotencyKey().trim().length() > 128) {
            throw new ReceptionWorkflowException("VALIDATION_FAILED");
        }
        Instant checkedInAt = Instant.now(clock);
        if (workflowRepository.hasVisitFromAppointment(command.appointmentId())) throw new ReceptionWorkflowException("VISIT_LINK_CONFLICT");

        // Clinic day and exam fee are decided by the appointment side before this call; a recorded arrival only
        // supplies the arrival time, whatever that time is.
        java.util.List<Object[]> arrivals = workflowRepository.findRecordedArrival(command.appointmentId());
        Instant arrivedAt = checkedInAt;
        if (!arrivals.isEmpty()) {
            Object[] evidence = arrivals.getFirst();
            if (!command.patientProfileId().equals(evidence[0])) {
                throw new ReceptionWorkflowException("VISIT_LINK_CONFLICT");
            }
            arrivedAt = toInstant(evidence[1]);
        }

        UUID visitId = UUID.randomUUID();
        workflowRepository.insertCheckInVisit(visitId, new CheckInData(command.appointmentId(),
                command.patientProfileId(), command.actorAccountId(), arrivedAt, checkedInAt));
        ExamQueueTicket ticket = enqueueCheckedInVisit(visitId, command.doctorProfileId(), checkedInAt);
        return new CheckInResult(command.appointmentId(), visitId, command.patientProfileId(),
                arrivedAt, checkedInAt, false, ticket);
    }

    private ExamQueueTicket enqueueCheckedInVisit(UUID visitId, UUID doctorProfileId, Instant checkedInAt) {
        try {
            return examQueue.enqueueWaiting(visitId, doctorProfileId, null, checkedInAt);
        } catch (VisitOperationException failure) {
            throw new ReceptionWorkflowException(failure.code());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasConsistentCheckInEvidence(UUID appointmentId, UUID patientProfileId, UUID visitId) {
        if (appointmentId == null || patientProfileId == null || visitId == null) return false;
        return workflowRepository.hasConsistentCheckInEvidence(appointmentId, patientProfileId, visitId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExamQueueTicket> findExamQueueTicket(UUID visitId) {
        if (visitId == null) return Optional.empty();
        return examQueue.find(visitId).filter(entry -> entry.queueNumber() != null)
                .map(entry -> new ExamQueueTicket(entry.queueNumber(), entry.doctorProfileId(),
                        doctorName(entry.doctorProfileId()), entry.roomId(), roomLabel(entry.roomId())));
    }

    /** Display rules match the exam-queue read model: doctor name from staff, room label from the visit room. */
    private String doctorName(UUID doctorProfileId) {
        return doctorProfiles.findDoctorNames(List.of(doctorProfileId)).get(doctorProfileId);
    }

    private String roomLabel(UUID roomId) {
        return roomId == null ? null : visitRooms.findById(roomId).map(VisitRoomJpaEntity::getDisplayLabel).orElse(null);
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected JPA timestamp value: " + value.getClass().getName());
    }
}
