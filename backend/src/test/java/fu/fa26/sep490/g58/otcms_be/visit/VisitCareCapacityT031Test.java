package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckVisitReservationCapacityService;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.config.VisitCareProperties;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitCapacityConflictException;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitCareAssignmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.RoomReservationPlan;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentAuditRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitRoomCapacityService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.CompleteWalkInCareStepCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExtendVisitCareAssignmentCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.StartWalkInCareStepCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.ManageVisitCareAssignmentServiceImpl;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisitCareCapacityT031Test {
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private final UUID visitId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID doctorAccountId = UUID.randomUUID();
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final DoctorProfileIdentityQueryService doctors = mock(DoctorProfileIdentityQueryService.class);
    private final CheckVisitReservationCapacityService appointments = mock(CheckVisitReservationCapacityService.class);
    private final VisitCareAssignmentRepository assignments = mock(VisitCareAssignmentRepository.class);
    private final VisitCareAssignmentAuditRepository audits = mock(VisitCareAssignmentAuditRepository.class);
    private final VisitRoomCapacityService rooms = mock(VisitRoomCapacityService.class);
    private final VisitCareProperties properties = new VisitCareProperties();
    private final ExitWalkInReceptionQueueService queueExit = mock(ExitWalkInReceptionQueueService.class);
    private final ManageVisitCareAssignmentServiceImpl service = new ManageVisitCareAssignmentServiceImpl(guard,
            doctors, appointments, assignments, audits, rooms, properties, queueExit, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        properties.setCareStepDurationMinutes(java.util.Map.of("CONSULTATION", 30, "RESULT_REVIEW", 15));
        properties.setCareStepRoomCapability(java.util.Map.of("CONSULTATION", "NONE", "RESULT_REVIEW", "NONE"));
        when(rooms.prepare(any(), any(), any(), any())).thenAnswer(invocation ->
                new RoomReservationPlan(null, null, invocation.getArgument(0), "NONE",
                        invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(2), 0));
        when(doctors.findDoctorProfileId(doctorAccountId)).thenReturn(Optional.of(doctorId));
        when(assignments.countDoctorConflicts(any(), any(), any(), any())).thenReturn(0L);
        when(assignments.countVisitActiveCare(any(), any())).thenReturn(0L);
        when(appointments.checkVisitReservation(any())).thenReturn(
                new CheckVisitReservationCapacityService.CapacityDecision(true, null, null));
    }

    @Test
    void startsWalkInWithDerivedDoctorConfiguredDurationAndImmutableAudit() {
        when(assignments.findVisitForUpdate(visitId)).thenReturn(java.util.Collections.singletonList(visitRow("WALK_IN", null)));

        var created = service.startWalkIn(new StartWalkInCareStepCommand(visitId, doctorAccountId,
                AccountRole.DOCTOR, "CONSULTATION", "corr-t031"));

        assertEquals(doctorId, created.doctorProfileId());
        assertEquals(NOW.plusSeconds(1800), created.expectedEnd());
        assertEquals("IN_PROGRESS", created.status());
        verify(queueExit).exit(new ExitWalkInReceptionQueueCommand(visitId, doctorAccountId,
                AccountRole.DOCTOR, "CARE_STARTED", NOW, "corr-t031"));
        var order = inOrder(guard, assignments, appointments, audits);
        order.verify(guard).acquire();
        order.verify(assignments).findVisitForUpdate(visitId);
        order.verify(appointments).checkVisitReservation(any());
        order.verify(assignments).saveAndFlush(any(VisitCareAssignmentJpaEntity.class));
        verify(rooms).reserve(any(RoomReservationPlan.class), eq(visitId), eq(created.id()), eq(doctorAccountId),
                eq("corr-t031"), eq(NOW));
        order.verify(audits).append(eq(visitId), eq(created.id()), eq(doctorAccountId), eq("STARTED"),
                eq("CONSULTATION"), eq(null), eq(Timestamp.from(created.expectedEnd())),
                eq(Timestamp.from(NOW)), eq("corr-t031"));
    }

    @Test
    void missingStepConfigurationFailsClosedWithoutReservationOrWrite() {
        properties.setCareStepDurationMinutes(java.util.Map.of());
        when(assignments.findVisitForUpdate(visitId)).thenReturn(java.util.Collections.singletonList(visitRow("WALK_IN", null)));

        var failure = assertThrows(VisitOperationException.class, () -> service.startWalkIn(
                new StartWalkInCareStepCommand(visitId, doctorAccountId, AccountRole.DOCTOR,
                        "RESULT_REVIEW", "corr-t031")));

        assertEquals("VISIT_CARE_STEP_NOT_CONFIGURED", failure.code());
        verify(assignments, never()).saveAndFlush(any());
        verifyNoInteractions(appointments, audits);
    }

    @Test
    void appointmentConflictDoesNotCreateCareAssignment() {
        when(assignments.findVisitForUpdate(visitId)).thenReturn(java.util.Collections.singletonList(visitRow("WALK_IN", null)));
        when(appointments.checkVisitReservation(any()))
                .thenReturn(new CheckVisitReservationCapacityService.CapacityDecision(false, appointmentId, null));

        var failure = assertThrows(VisitCapacityConflictException.class, () -> service.startWalkIn(
                new StartWalkInCareStepCommand(visitId, doctorAccountId, AccountRole.DOCTOR,
                        "CONSULTATION", "corr-t031")));

        assertEquals("VISIT_CAPACITY_NOT_AVAILABLE", failure.getMessage());
        verify(assignments, never()).saveAndFlush(any());
        verify(audits).appendConflict(eq(visitId), eq(null), eq(doctorAccountId), eq("corr-t031"),
                eq("CONSULTATION"), eq(Timestamp.from(NOW)));
    }

    @Test
    void extendsScheduledCareUsingOnlyNewFutureIntervalAndPreservesAppointmentLink() {
        Instant startsAt = NOW.minusSeconds(900);
        Instant oldEnd = NOW.plusSeconds(600);
        when(assignments.findVisitForUpdate(visitId)).thenReturn(java.util.Collections.singletonList(visitRow("SCHEDULED", appointmentId)));
        when(appointments.checkVisitReservation(any())).thenReturn(
                new CheckVisitReservationCapacityService.CapacityDecision(true, null, appointmentId));
        when(assignments.findAssignmentForUpdate(assignmentId, visitId)).thenReturn(java.util.Collections.singletonList(new Object[]{assignmentId, visitId, doctorId, Timestamp.from(startsAt), Timestamp.from(oldEnd), "IN_PROGRESS", "SCHEDULED_APPOINTMENT", 30, null}));
        when(assignments.extend(eq(assignmentId), eq(visitId), any(), eq(45))).thenReturn(1);

        var updated = service.extend(new ExtendVisitCareAssignmentCommand(visitId, assignmentId,
                doctorAccountId, AccountRole.DOCTOR, "RESULT_REVIEW", "corr-t031"));

        assertEquals(oldEnd.plusSeconds(900), updated.expectedEnd());
        verify(assignments).countDoctorConflicts(eq(doctorId), eq(Timestamp.from(oldEnd)),
                eq(Timestamp.from(updated.expectedEnd())), eq(assignmentId));
        verify(appointments).checkVisitReservation(argThat(arg -> {
            var reservation = (CheckVisitReservationCapacityService.VisitReservation) arg;
            return reservation.linkedAppointmentId().equals(appointmentId)
                    && reservation.startInclusive().equals(oldEnd)
                    && reservation.endExclusive().equals(updated.expectedEnd());
        }));
        verify(assignments).extend(assignmentId, visitId, Timestamp.from(updated.expectedEnd()), 45);
        verify(audits).append(visitId, assignmentId, doctorAccountId, "EXTENDED", "RESULT_REVIEW",
                Timestamp.from(oldEnd), Timestamp.from(updated.expectedEnd()), Timestamp.from(NOW), "corr-t031");
    }

    @Test
    void completesWalkInWithServerActualEndWithoutClosingVisit() {
        Instant startsAt = NOW.minusSeconds(600);
        Instant expectedEnd = NOW.plusSeconds(1200);
        when(assignments.findVisitForUpdate(visitId)).thenReturn(java.util.Collections.singletonList(visitRow("WALK_IN", null)));
        when(assignments.findAssignmentForUpdate(assignmentId, visitId)).thenReturn(java.util.Collections.singletonList(new Object[]{assignmentId, visitId, doctorId, Timestamp.from(startsAt), Timestamp.from(expectedEnd), "IN_PROGRESS", "CONSULTATION", 30, null}));
        when(assignments.complete(eq(assignmentId), eq(visitId), eq(doctorId), any())).thenReturn(1);

        var completed = service.completeWalkIn(new CompleteWalkInCareStepCommand(visitId, assignmentId,
                doctorAccountId, AccountRole.DOCTOR, "corr-t031"));

        assertEquals("COMPLETED", completed.status());
        assertEquals(NOW, completed.actualEndedAt());
        assertEquals(expectedEnd, completed.expectedEnd());
        verify(assignments).complete(assignmentId, visitId, doctorId, Timestamp.from(NOW));
        verify(rooms).complete(visitId, assignmentId, doctorAccountId, "corr-t031", NOW);
    }

    @Test
    void nonDoctorCannotAcquireCapacityOrMutateVisit() {
        var failure = assertThrows(VisitOperationException.class, () -> service.startWalkIn(
                new StartWalkInCareStepCommand(visitId, doctorAccountId, AccountRole.RECEPTIONIST,
                        "CONSULTATION", "corr-t031")));

        assertEquals("ACCESS_DENIED", failure.code());
        verifyNoInteractions(guard, doctors, appointments, assignments, audits);
    }

    private Object[] visitRow(String source, UUID linkedAppointmentId) {
        return new Object[]{visitId, patientId, source, linkedAppointmentId, "OPEN"};
    }
}
