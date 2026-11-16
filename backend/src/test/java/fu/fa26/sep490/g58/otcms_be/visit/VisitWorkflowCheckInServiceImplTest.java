package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalFollowUpEvidenceQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.ReceptionWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueTicket;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitWorkflowRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ReceptionWorkflowService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitRoomCapacityService;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInVisitLifecycleService;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitWorkflowServiceImpl;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisitWorkflowCheckInServiceImplTest {
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-14T05:00:00Z");
    private final VisitWorkflowRepository repository = mock(VisitWorkflowRepository.class);
    private final VisitExamQueueService examQueue = mock(VisitExamQueueService.class);
    private final DoctorProfileIdentityQueryService doctorProfiles = mock(DoctorProfileIdentityQueryService.class);
    private final VisitRoomJpaRepository visitRooms = mock(VisitRoomJpaRepository.class);
    private final VisitWorkflowServiceImpl visits = new VisitWorkflowServiceImpl(repository,
            Clock.fixed(now, ZoneOffset.UTC), mock(WalkInVisitLifecycleService.class),
            mock(ClinicalFollowUpEvidenceQueryService.class), examQueue, doctorProfiles, visitRooms, mock(fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitAuditRecorder.class));
    private final ExamQueueTicket ticket = new ExamQueueTicket(1, doctorId, "Dr. A", roomId, "Room 2");

    @BeforeEach
    void setUp() {
        when(repository.hasVisitFromAppointment(appointmentId)).thenReturn(false);
        when(repository.findRecordedArrival(appointmentId)).thenReturn(List.of());
        when(examQueue.enqueueWaiting(any(), eq(doctorId), isNull(), eq(now))).thenReturn(ticket);
    }

    @Test
    void checkInQueuesTheVisitUnderTheAppointmentDoctorAndReturnsTheTicket() {
        var result = visits.checkInAtomically(command(doctorId));

        assertEquals(appointmentId, result.appointmentId());
        assertEquals(now, result.checkedInAt());
        assertEquals(now, result.arrivedAt());
        assertEquals(ticket, result.ticket());
        verify(repository).insertCheckInVisit(eq(result.visitId()), any());
        verify(examQueue).enqueueWaiting(result.visitId(), doctorId, null, now);
    }

    @Test
    void recordedArrivalOutsideAnyWindowStillChecksInAndKeepsItsTime() {
        // A receptionist's early manual arrival is evidence of the arrival time only; it no longer blocks check-in.
        Instant twoHoursEarly = now.minusSeconds(7200);
        Object[] evidence = {patientId, Timestamp.from(twoHoursEarly)};
        when(repository.findRecordedArrival(appointmentId)).thenReturn(Collections.singletonList(evidence));

        var result = visits.checkInAtomically(command(doctorId));

        assertEquals(twoHoursEarly, result.arrivedAt());
        assertEquals(ticket, result.ticket());
    }

    @Test
    void recordedArrivalOfAnotherPatientConflictsBeforeAnyInsert() {
        Object[] otherPatient = {UUID.randomUUID(), Timestamp.from(now)};
        when(repository.findRecordedArrival(appointmentId)).thenReturn(Collections.singletonList(otherPatient));

        assertEquals("VISIT_LINK_CONFLICT", assertThrows(ReceptionWorkflowException.class,
                () -> visits.checkInAtomically(command(doctorId))).code());
        verify(repository, never()).insertCheckInVisit(any(), any());
        verifyNoInteractions(examQueue);
    }

    @Test
    void missingDoctorIsRejectedBeforeTouchingStorage() {
        assertEquals("VALIDATION_FAILED", assertThrows(ReceptionWorkflowException.class,
                () -> visits.checkInAtomically(command(null))).code());
        verifyNoInteractions(examQueue);
        verify(repository, never()).insertCheckInVisit(any(), any());
    }

    @Test
    void queueRejectionIsSurfacedWithItsCode() {
        when(examQueue.enqueueWaiting(any(), eq(doctorId), isNull(), eq(now)))
                .thenThrow(new VisitOperationException("VISIT_EXAM_STATE_CONFLICT"));

        assertEquals("VISIT_EXAM_STATE_CONFLICT", assertThrows(ReceptionWorkflowException.class,
                () -> visits.checkInAtomically(command(doctorId))).code());
    }

    @Test
    void findExamQueueTicketNamesTheDoctorAndTheRoomOfTheVisit() {
        UUID visitId = UUID.randomUUID();
        when(examQueue.find(visitId)).thenReturn(Optional.of(entry(visitId, 2)));
        when(doctorProfiles.findDoctorNames(List.of(doctorId))).thenReturn(Map.of(doctorId, "Dr. A"));
        var room = mock(VisitRoomJpaEntity.class);
        when(room.getDisplayLabel()).thenReturn("Room 2");
        when(visitRooms.findById(roomId)).thenReturn(Optional.of(room));

        assertEquals(Optional.of(new ExamQueueTicket(2, doctorId, "Dr. A", roomId, "Room 2")),
                visits.findExamQueueTicket(visitId));
    }

    @Test
    void findExamQueueTicketIsEmptyWithoutARowOrWithoutANumber() {
        UUID visitId = UUID.randomUUID();
        when(examQueue.find(visitId)).thenReturn(Optional.empty());
        assertEquals(Optional.empty(), visits.findExamQueueTicket(visitId));

        when(examQueue.find(visitId)).thenReturn(Optional.of(new ExamQueueEntry(visitId, doctorId, null, null, null,
                VisitExamStatus.PENDING_PAYMENT, LocalDate.of(2026, 10, 14), null, null, null, null)));
        assertEquals(Optional.empty(), visits.findExamQueueTicket(visitId));
    }

    private ReceptionWorkflowService.CheckInCommand command(UUID doctor) {
        return new ReceptionWorkflowService.CheckInCommand(actorId, appointmentId, patientId, doctor, "t053-key");
    }

    private ExamQueueEntry entry(UUID visitId, int queueNumber) {
        return new ExamQueueEntry(visitId, doctorId, roomId, null, queueNumber, VisitExamStatus.WAITING,
                LocalDate.of(2026, 10, 14), now, null, null, null);
    }
}
