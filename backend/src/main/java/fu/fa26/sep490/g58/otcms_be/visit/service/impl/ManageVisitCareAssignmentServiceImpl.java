package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckVisitReservationCapacityService;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentCapacityUnavailableException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckVisitReservationCapacityService.VisitReservation;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.config.VisitCareProperties;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitCapacityConflictException;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitRoomCapacityConflictException;
import fu.fa26.sep490.g58.otcms_be.visit.model.CareStepAssignment;
import fu.fa26.sep490.g58.otcms_be.visit.model.RoomReservationPlan;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitCareAssignmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentAuditRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.CompleteWalkInCareStepCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExtendVisitCareAssignmentCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitCareAssignmentService;
import fu.fa26.sep490.g58.otcms_be.visit.service.RecordVisitCapacityConflictCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.StartWalkInCareStepCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitRoomCapacityService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageVisitCareAssignmentServiceImpl implements ManageVisitCareAssignmentService {
    private final CoordinationGuardService guard;
    private final DoctorProfileIdentityQueryService doctors;
    private final CheckVisitReservationCapacityService appointments;
    private final VisitCareAssignmentRepository assignments;
    private final VisitCareAssignmentAuditRepository audits;
    private final VisitRoomCapacityService rooms;
    private final VisitCareProperties properties;
    private final Clock clock;

    public ManageVisitCareAssignmentServiceImpl(CoordinationGuardService guard, DoctorProfileIdentityQueryService doctors,
            CheckVisitReservationCapacityService appointments, VisitCareAssignmentRepository assignments,
            VisitCareAssignmentAuditRepository audits, VisitRoomCapacityService rooms,
            VisitCareProperties properties, Clock clock) {
        this.guard = guard;
        this.doctors = doctors;
        this.appointments = appointments;
        this.assignments = assignments;
        this.audits = audits;
        this.rooms = rooms;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = VisitCapacityConflictException.class)
    public CareStepAssignment startWalkIn(StartWalkInCareStepCommand command) {
        validate(command);
        requireDoctor(command.actorRole());
        acquireGuard();
        UUID doctorId = resolveDoctor(command.actorAccountId());
        Object[] visit = lockVisit(command.visitId());
        if (!"WALK_IN".equals(visit[2]) || !"OPEN".equals(visit[4])) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        int duration = configuredDuration(command.stepCode());
        Instant now = clock.instant();
        Instant expectedEnd = safePlusMinutes(now, duration);
        try {
            ensureDoctorCapacity(command.visitId(), (UUID) visit[1], doctorId, now, expectedEnd, null, null);
        } catch (VisitOperationException exception) {
            if ("VISIT_CAPACITY_NOT_AVAILABLE".equals(exception.code())
                    || "VISIT_LINK_CONFLICT".equals(exception.code())) {
                audits.appendConflict(command.visitId(), null, command.actorAccountId(), command.correlationId(),
                        command.stepCode(), Timestamp.from(now));
                throw new VisitCapacityConflictException(exception.code());
            }
            throw exception;
        }
        ensureNoActiveVisitCare(command.visitId(), null);

        RoomReservationPlan roomPlan = prepareRoom(command.visitId(), null, command.actorAccountId(),
                command.stepCode(), command.correlationId(), now, now, expectedEnd);

        UUID assignmentId = UUID.randomUUID();
        assignments.saveAndFlush(VisitCareAssignmentJpaEntity.inProgress(assignmentId, command.visitId(), doctorId,
                command.stepCode(), duration, now, expectedEnd));
        rooms.reserve(roomPlan, command.visitId(), assignmentId, command.actorAccountId(),
                command.correlationId(), now);
        audits.append(command.visitId(), assignmentId, command.actorAccountId(), "STARTED", command.stepCode(),
                null, Timestamp.from(expectedEnd), Timestamp.from(now), command.correlationId());
        return new CareStepAssignment(assignmentId, command.visitId(), doctorId, command.stepCode(), "IN_PROGRESS",
                now, expectedEnd, null, roomPlan.roomId(), roomPlan.roomCode());
    }

    @Override
    @Transactional(noRollbackFor = VisitCapacityConflictException.class)
    public CareStepAssignment extend(ExtendVisitCareAssignmentCommand command) {
        validate(command);
        requireDoctor(command.actorRole());
        acquireGuard();
        UUID doctorId = resolveDoctor(command.actorAccountId());
        Object[] visit = lockVisit(command.visitId());
        if (!"OPEN".equals(visit[4])) throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        Object[] current = lockAssignment(command.assignmentId(), command.visitId());
        CareStepAssignment assignment = toAssignment(current);
        if (!doctorId.equals(assignment.doctorProfileId()) || !"IN_PROGRESS".equals(assignment.status())) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        int additionalMinutes = configuredDuration(command.stepCode());
        Instant now = clock.instant();
        Instant extensionFrom = now.isAfter(assignment.expectedEnd()) ? now : assignment.expectedEnd();
        Instant newExpectedEnd = safePlusMinutes(extensionFrom, additionalMinutes);
        RoomReservationPlan roomPlan = prepareRoom(command.visitId(), assignment.id(), command.actorAccountId(),
                command.stepCode(), command.correlationId(), now, extensionFrom, newExpectedEnd);
        try {
            ensureDoctorCapacity(command.visitId(), (UUID) visit[1], doctorId, extensionFrom, newExpectedEnd,
                    assignment.id(), (UUID) visit[3]);
        } catch (VisitOperationException exception) {
            if ("VISIT_CAPACITY_NOT_AVAILABLE".equals(exception.code())
                    || "VISIT_LINK_CONFLICT".equals(exception.code())) {
                audits.appendConflict(command.visitId(), assignment.id(), command.actorAccountId(),
                        command.correlationId(), command.stepCode(), Timestamp.from(now));
                throw new VisitCapacityConflictException(exception.code());
            }
            throw exception;
        }
        long totalDuration = (long) assignmentDuration(current) + additionalMinutes;
        if (totalDuration > Integer.MAX_VALUE) throw new VisitOperationException("VALIDATION_FAILED");
        if (assignments.extend(assignment.id(), command.visitId(), Timestamp.from(newExpectedEnd), (int) totalDuration) != 1) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        rooms.extend(roomPlan, command.visitId(), assignment.id(), command.actorAccountId(),
                command.correlationId(), now);
        audits.append(command.visitId(), assignment.id(), command.actorAccountId(), "EXTENDED", command.stepCode(),
                Timestamp.from(assignment.expectedEnd()), Timestamp.from(newExpectedEnd), Timestamp.from(now),
                command.correlationId());
        return new CareStepAssignment(assignment.id(), assignment.visitId(), assignment.doctorProfileId(),
                assignment.stepCode(), assignment.status(), assignment.startsAt(), newExpectedEnd, null,
                roomPlan.roomId(), roomPlan.roomCode());
    }

    @Override
    @Transactional
    public CareStepAssignment completeWalkIn(CompleteWalkInCareStepCommand command) {
        validate(command);
        requireDoctor(command.actorRole());
        acquireGuard();
        UUID doctorId = resolveDoctor(command.actorAccountId());
        Object[] visit = lockVisit(command.visitId());
        if (!"WALK_IN".equals(visit[2]) || !"OPEN".equals(visit[4])) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        Object[] current = lockAssignment(command.assignmentId(), command.visitId());
        CareStepAssignment assignment = toAssignment(current);
        if (!doctorId.equals(assignment.doctorProfileId()) || !"IN_PROGRESS".equals(assignment.status())) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        Instant actualEnd = clock.instant();
        if (actualEnd.isBefore(assignment.startsAt())) throw new VisitOperationException("VALIDATION_FAILED");
        if (assignments.complete(assignment.id(), assignment.visitId(), doctorId, Timestamp.from(actualEnd)) != 1) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        rooms.complete(command.visitId(), assignment.id(), command.actorAccountId(), command.correlationId(), actualEnd);
        audits.append(command.visitId(), assignment.id(), command.actorAccountId(), "COMPLETED", assignment.stepCode(),
                Timestamp.from(assignment.expectedEnd()), Timestamp.from(assignment.expectedEnd()),
                Timestamp.from(actualEnd), command.correlationId());
        return new CareStepAssignment(assignment.id(), assignment.visitId(), assignment.doctorProfileId(),
                assignment.stepCode(), "COMPLETED", assignment.startsAt(), assignment.expectedEnd(), actualEnd);
    }

    @Override
    @Transactional
    public void recordCapacityConflict(RecordVisitCapacityConflictCommand command) {
        if (command == null || command.visitId() == null || command.assignmentId() == null
                || command.actorAccountId() == null || command.actorRole() != AccountRole.DOCTOR
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        acquireGuard();
        UUID doctorId = resolveDoctor(command.actorAccountId());
        Object[] visit = lockVisit(command.visitId());
        if (!"OPEN".equals(visit[4])) throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        Object[] current = lockAssignment(command.assignmentId(), command.visitId());
        if (!doctorId.equals(current[2]) || !"IN_PROGRESS".equals(current[5])) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        Instant now = clock.instant();
        audits.appendConflict(command.visitId(), command.assignmentId(), command.actorAccountId(),
                command.correlationId(), "CAPACITY_CONFLICT", Timestamp.from(now));
    }

    private void ensureDoctorCapacity(UUID visitId, UUID patientId, UUID doctorId, Instant from, Instant to,
            UUID excludedAssignmentId, UUID linkedAppointmentId) {
        if (assignments.countDoctorConflicts(doctorId, Timestamp.from(from), Timestamp.from(to), excludedAssignmentId) > 0) {
            throw new VisitOperationException("VISIT_CAPACITY_NOT_AVAILABLE");
        }
        CheckVisitReservationCapacityService.CapacityDecision decision;
        try {
            decision = appointments.checkVisitReservation(
                    new VisitReservation(doctorId, from, to, visitId, linkedAppointmentId, patientId));
        } catch (AppointmentCapacityUnavailableException | DataAccessException exception) {
            throw new VisitOperationException("DEPENDENCY_UNAVAILABLE", exception);
        }
        if (linkedAppointmentId != null && !linkedAppointmentId.equals(decision.validatedLinkedAppointmentId())) {
            throw new VisitOperationException("VISIT_LINK_CONFLICT");
        }
        if (!decision.available()) throw new VisitOperationException("VISIT_CAPACITY_NOT_AVAILABLE");
    }

    private void ensureNoActiveVisitCare(UUID visitId, UUID excludedAssignmentId) {
        if (assignments.countVisitActiveCare(visitId, excludedAssignmentId) > 0) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
    }

    private RoomReservationPlan prepareRoom(UUID visitId, UUID assignmentId, UUID actorAccountId,
            String stepCode, String correlationId, Instant occurredAt, Instant startsAt, Instant expectedEnd) {
        try {
            return rooms.prepare(stepCode, startsAt, expectedEnd, assignmentId);
        } catch (VisitRoomCapacityConflictException exception) {
            audits.appendConflict(visitId, assignmentId, actorAccountId, correlationId,
                    stepCode, Timestamp.from(occurredAt));
            rooms.recordConflict(visitId, assignmentId, actorAccountId, stepCode, correlationId, occurredAt);
            throw new VisitCapacityConflictException("VISIT_CAPACITY_NOT_AVAILABLE");
        }
    }

    private UUID resolveDoctor(UUID accountId) {
        try {
            return doctors.findDoctorProfileId(accountId)
                    .orElseThrow(() -> new VisitOperationException("ACCESS_DENIED"));
        } catch (DataAccessException exception) {
            throw new VisitOperationException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    private int configuredDuration(String stepCode) {
        if (stepCode == null || !stepCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        return properties.durationMinutes(stepCode)
                .orElseThrow(() -> new VisitOperationException("VISIT_CARE_STEP_NOT_CONFIGURED"));
    }

    private void acquireGuard() {
        try {
            guard.acquire();
        } catch (DataAccessException exception) {
            throw new VisitOperationException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    private Object[] lockVisit(UUID visitId) {
        List<Object[]> rows = assignments.findVisitForUpdate(visitId);
        if (rows.isEmpty()) throw new VisitOperationException("RESOURCE_NOT_FOUND");
        return rows.getFirst();
    }

    private Object[] lockAssignment(UUID assignmentId, UUID visitId) {
        List<Object[]> rows = assignments.findAssignmentForUpdate(assignmentId, visitId);
        if (rows.isEmpty()) throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        return rows.getFirst();
    }

    private static CareStepAssignment toAssignment(Object[] row) {
        return new CareStepAssignment((UUID) row[0], (UUID) row[1], (UUID) row[2], (String) row[6], (String) row[5],
                toInstant(row[3]), toInstant(row[4]), row[8] == null ? null : toInstant(row[8]));
    }

    private static int assignmentDuration(Object[] row) {
        if (row[7] instanceof Number value) return value.intValue();
        return Math.toIntExact(java.time.Duration.between(toInstant(row[3]), toInstant(row[4])).toMinutes());
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new VisitOperationException("DEPENDENCY_UNAVAILABLE");
    }

    private static Instant safePlusMinutes(Instant instant, int minutes) {
        try {
            return instant.plusSeconds(Math.multiplyExact((long) minutes, 60L));
        } catch (ArithmeticException exception) {
            throw new VisitOperationException("VALIDATION_FAILED", exception);
        }
    }

    private static void requireDoctor(AccountRole role) {
        if (role != AccountRole.DOCTOR) throw new VisitOperationException("ACCESS_DENIED");
    }

    private static void validate(StartWalkInCareStepCommand command) {
        if (command == null || command.visitId() == null || command.actorAccountId() == null
                || command.actorRole() == null || command.correlationId() == null
                || command.correlationId().isBlank() || command.correlationId().length() > 128) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
    }

    private static void validate(ExtendVisitCareAssignmentCommand command) {
        if (command == null || command.visitId() == null || command.assignmentId() == null
                || command.actorAccountId() == null || command.actorRole() == null || command.correlationId() == null
                || command.correlationId().isBlank() || command.correlationId().length() > 128) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
    }

    private static void validate(CompleteWalkInCareStepCommand command) {
        if (command == null || command.visitId() == null || command.assignmentId() == null
                || command.actorAccountId() == null || command.actorRole() == null || command.correlationId() == null
                || command.correlationId().isBlank() || command.correlationId().length() > 128) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
    }
}
