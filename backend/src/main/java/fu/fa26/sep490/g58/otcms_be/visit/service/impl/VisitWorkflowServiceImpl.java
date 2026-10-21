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
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitRoomCapacityConflictException;
import fu.fa26.sep490.g58.otcms_be.visit.model.RoomReservationPlan;
import fu.fa26.sep490.g58.otcms_be.visit.model.ScheduledCarePersistenceData;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitRoomCapacityService;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitWorkflowRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitWorkflowRepository.CheckInData;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitWorkflowServiceImpl implements VisitWorkflowService, ReceptionWorkflowService, VisitScheduledCareService {
    private final VisitWorkflowRepository workflowRepository;
    private final Clock clock;
    private final VisitRoomCapacityService rooms;

    public VisitWorkflowServiceImpl(VisitWorkflowRepository workflowRepository, Clock clock, VisitRoomCapacityService rooms) {
        this.workflowRepository = workflowRepository; this.clock = clock; this.rooms = rooms;
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpEvidence requireFollowUpEvidence(UUID visitId, UUID authenticatedDoctorProfileId) {
        VisitWorkflowRepository.FollowUpEvidenceRow row = workflowRepository.findFollowUpEvidence(visitId, authenticatedDoctorProfileId);
        if (row == null) throw new VisitEvidenceException("SOURCE_VISIT_NOT_FOUND");
        return new FollowUpEvidence(visitId, row.patientProfileId(), authenticatedDoctorProfileId,
                row.assigned(), row.examinationCompleted(), row.followUpIndicated(), row.originatingAppointmentId(), clock.instant());
    }

    @Override
    @Transactional(noRollbackFor = VisitWorkflowException.class)
    public void startScheduledCare(ScheduledCareCommand command) {
        validateScheduledCare(command);
        if (!workflowRepository.lockScheduledVisit(command.visitId(), command.appointmentId(), command.patientProfileId()))
            throw new VisitWorkflowException("VISIT_LINK_CONFLICT");
        Instant now = command.occurredAt();
        if (!now.isBefore(command.scheduledEnd())) {
            appendCapacityConflict(command, now);
            throw new VisitWorkflowException("VISIT_CAPACITY_NOT_AVAILABLE");
        }
        if (workflowRepository.hasActiveCare(command.visitId())) throw new VisitWorkflowException("VISIT_CARE_NOT_STARTABLE");
        if (workflowRepository.hasDoctorCareConflict(command.doctorProfileId(), now, command.scheduledEnd())) {
            appendCapacityConflict(command, now);
            throw new VisitWorkflowException("VISIT_CAPACITY_NOT_AVAILABLE");
        }
        RoomReservationPlan roomPlan;
        try {
            roomPlan = rooms.prepare("SCHEDULED_APPOINTMENT", now, command.scheduledEnd(), null);
        } catch (VisitRoomCapacityConflictException conflict) {
            appendCapacityConflict(command, now);
            rooms.recordConflict(command.visitId(), null, command.actorAccountId(), "SCHEDULED_APPOINTMENT",
                    command.correlationId(), now);
            throw new VisitWorkflowException("VISIT_CAPACITY_NOT_AVAILABLE");
        } catch (VisitOperationException failure) {
            throw new VisitWorkflowException(failure.code(), failure);
        }
        UUID assignmentId = UUID.randomUUID();
        workflowRepository.insertScheduledCare(assignmentId, persistenceData(command), now);
        rooms.reserve(roomPlan, command.visitId(), assignmentId, command.actorAccountId(),
                command.correlationId(), now);
        appendCareAudit(command, "STARTED", "SCHEDULED_APPOINTMENT", null,
                Timestamp.from(command.scheduledEnd()), now, assignmentId);
    }

    @Override
    @Transactional
    public void completeScheduledCare(ScheduledCareCommand command) {
        validateScheduledCare(command);
        VisitWorkflowRepository.CompletedCareRow row = workflowRepository.completeScheduledCare(persistenceData(command));
        if (row == null) throw new VisitWorkflowException("VISIT_CARE_NOT_COMPLETABLE");
        Timestamp expectedEnd = row.expectedEnd();
        rooms.complete(command.visitId(), row.assignmentId(), command.actorAccountId(), command.correlationId(),
                command.occurredAt());
        appendCareAudit(command, "COMPLETED", row.stepCode(), expectedEnd,
                expectedEnd, command.occurredAt(), row.assignmentId());
    }

    @Override
    @Transactional
    public void cancelCheckedInVisit(ScheduledCareCommand command) {
        validateScheduledCare(command);
        if (!workflowRepository.lockScheduledVisit(command.visitId(), command.appointmentId(), command.patientProfileId()))
            throw new VisitWorkflowException("VISIT_LINK_CONFLICT");
        if (!workflowRepository.closeCancellableVisit(persistenceData(command))) throw new VisitWorkflowException("VISIT_CANCELLATION_NOT_ALLOWED");
        workflowRepository.releaseReservedCare(command.visitId(), command.occurredAt());
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
        workflowRepository.appendCareAudit(command.visitId(), careAssignmentId, command.actorAccountId(), action,
                stepCode == null ? "SCHEDULED_APPOINTMENT" : stepCode, oldEnd, newEnd, occurredAt,
                command.correlationId());
    }

    private void appendCapacityConflict(ScheduledCareCommand command, Instant occurredAt) {
        workflowRepository.appendCapacityConflict(command.visitId(), command.actorAccountId(), occurredAt,
                command.correlationId());
    }

    @Override
    @Transactional(noRollbackFor = ReceptionWorkflowException.class)
    public CheckInResult checkInAtomically(CheckInCommand command) {
        if (command == null || command.actorAccountId() == null || command.appointmentId() == null
                || command.patientProfileId() == null || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.idempotencyKey().trim().length() > 128
                || command.allowedWindowStartInclusive() == null || command.allowedWindowEndInclusive() == null
                || command.allowedWindowStartInclusive().isAfter(command.allowedWindowEndInclusive())) {
            throw new ReceptionWorkflowException("VALIDATION_FAILED");
        }
        Instant checkedInAt = Instant.now(clock);
        if (checkedInAt.isBefore(command.allowedWindowStartInclusive())
                || checkedInAt.isAfter(command.allowedWindowEndInclusive())) {
            throw new ReceptionWorkflowException("CHECK_IN_TIME_NOT_ALLOWED");
        }
        if (workflowRepository.hasVisitFromAppointment(command.appointmentId())) throw new ReceptionWorkflowException("VISIT_LINK_CONFLICT");

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
        return new CheckInResult(command.appointmentId(), visitId, command.patientProfileId(),
                arrivedAt, checkedInAt, false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasConsistentCheckInEvidence(UUID appointmentId, UUID patientProfileId, UUID visitId) {
        if (appointmentId == null || patientProfileId == null || visitId == null) return false;
        return workflowRepository.hasConsistentCheckInEvidence(appointmentId, patientProfileId, visitId);
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected JPA timestamp value: " + value.getClass().getName());
    }
}
