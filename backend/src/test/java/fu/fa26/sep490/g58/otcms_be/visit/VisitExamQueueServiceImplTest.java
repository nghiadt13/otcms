package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueTicket;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitExamQueueJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitExamQueueJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitExamQueueServiceImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

class VisitExamQueueServiceImplTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2030-01-15T02:00:00Z"); // 09:00 in the clinic zone
    private static final LocalDate CLINIC_DAY = LocalDate.of(2030, 1, 15);
    private static final UUID VISIT = UUID.randomUUID();
    private static final UUID DOCTOR = UUID.randomUUID();
    private static final UUID OTHER_DOCTOR = UUID.randomUUID();
    private static final UUID ROOM = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();

    private final VisitExamQueueJpaRepository queue = mock(VisitExamQueueJpaRepository.class);
    private final DoctorScheduleService schedules = mock(DoctorScheduleService.class);
    private final DoctorProfileIdentityQueryService doctorProfiles = mock(DoctorProfileIdentityQueryService.class);
    private final VisitRoomJpaRepository rooms = mock(VisitRoomJpaRepository.class);
    private final AppointmentBookingProperties booking = mock(AppointmentBookingProperties.class);
    private final VisitExamQueueServiceImpl service =
            new VisitExamQueueServiceImpl(queue, schedules, doctorProfiles, rooms, booking);

    @BeforeEach
    void setUp() {
        when(booking.clinicTimeZone()).thenReturn(ZONE);
        when(doctorProfiles.findDoctorNames(anyCollection()))
                .thenReturn(Map.of(DOCTOR, "Dr An", OTHER_DOCTOR, "Dr Binh"));
    }

    @Test
    void enqueueWaitingTakesTheNextNumberOfTheDoctorsClinicDay() {
        when(queue.findById(VISIT)).thenReturn(Optional.empty());
        when(queue.findMaxQueueNumber(DOCTOR, CLINIC_DAY)).thenReturn(2);
        doctorRoomAt(ROOM);
        roomLabel(ROOM, "Phong 1");

        ExamQueueTicket ticket = service.enqueueWaiting(VISIT, DOCTOR, null, NOW);

        assertEquals(3, ticket.queueNumber());
        assertEquals("Dr An", ticket.doctorName());
        assertEquals("Phong 1", ticket.roomLabel());
        ArgumentCaptor<VisitExamQueueJpaEntity> saved = ArgumentCaptor.forClass(VisitExamQueueJpaEntity.class);
        verify(queue).save(saved.capture());
        assertEquals(VisitExamStatus.WAITING, saved.getValue().getExamStatus());
        assertEquals(CLINIC_DAY, saved.getValue().getClinicDate());
        assertEquals(ROOM, saved.getValue().getRoomId());
        assertEquals(NOW, saved.getValue().getQueuedAt());
    }

    @Test
    void enqueueWaitingReplayReturnsTheSameTicketAndOtherStatesConflict() {
        existingRow(waitingRow(3));
        doctorRoomAt(ROOM);
        roomLabel(ROOM, "Phong 1");

        assertEquals(3, service.enqueueWaiting(VISIT, DOCTOR, null, NOW).queueNumber());
        verify(queue, never()).save(any());

        VisitExamQueueJpaEntity examining = waitingRow(3);
        examining.startExamination(NOW);
        existingRow(examining);
        assertConflict(() -> service.enqueueWaiting(VISIT, DOCTOR, null, NOW));
    }

    @Test
    void walkInFeeFlowCreatesPendingThenAdmitsOnceWithANumber() {
        when(queue.existsById(VISIT)).thenReturn(false);
        service.createPendingPayment(VISIT, DOCTOR, SERVICE, NOW);
        ArgumentCaptor<VisitExamQueueJpaEntity> created = ArgumentCaptor.forClass(VisitExamQueueJpaEntity.class);
        verify(queue).save(created.capture());
        VisitExamQueueJpaEntity row = created.getValue();
        assertEquals(VisitExamStatus.PENDING_PAYMENT, row.getExamStatus());
        assertNull(row.getQueueNumber());

        existingRow(row);
        assertEquals(Optional.of(SERVICE), service.requiredFeeServiceId(VISIT));
        assertEquals(VisitExamStatus.PENDING_PAYMENT, service.find(VISIT).orElseThrow().examStatus());

        when(queue.findMaxQueueNumber(DOCTOR, CLINIC_DAY)).thenReturn(0);
        doctorRoomAt(ROOM);
        roomLabel(ROOM, "Phong 1");
        ExamQueueTicket admitted = service.admitAfterPayment(VISIT, NOW);

        assertEquals(1, admitted.queueNumber());
        assertEquals(VisitExamStatus.WAITING, row.getExamStatus());
        assertEquals(Optional.empty(), service.requiredFeeServiceId(VISIT));
        assertEquals(1, service.admitAfterPayment(VISIT, NOW).queueNumber());
        verify(queue, times(1)).findMaxQueueNumber(DOCTOR, CLINIC_DAY);
    }

    @Test
    void createAndAdmitRejectRowsInTheWrongState() {
        when(queue.existsById(VISIT)).thenReturn(true);
        assertConflict(() -> service.createPendingPayment(VISIT, DOCTOR, SERVICE, NOW));

        VisitExamQueueJpaEntity done = waitingRow(1);
        done.finish(NOW);
        existingRow(done);
        assertConflict(() -> service.admitAfterPayment(VISIT, NOW));

        when(queue.findById(VISIT)).thenReturn(Optional.empty());
        assertConflict(() -> service.admitAfterPayment(VISIT, NOW));
    }

    @Test
    void startExaminationNeedsTheResponsibleDoctorAndNoOtherExamOfTheirs() {
        existingRow(waitingRow(1));
        assertConflict(() -> service.startExamination(VISIT, OTHER_DOCTOR, NOW));

        when(queue.existsByDoctorProfileIdAndExamStatusAndVisitIdNot(DOCTOR, VisitExamStatus.IN_EXAMINATION, VISIT))
                .thenReturn(true);
        assertConflict(() -> service.startExamination(VISIT, DOCTOR, NOW));

        when(queue.existsByDoctorProfileIdAndExamStatusAndVisitIdNot(DOCTOR, VisitExamStatus.IN_EXAMINATION, VISIT))
                .thenReturn(false);
        VisitExamQueueJpaEntity row = waitingRow(1);
        existingRow(row);
        service.startExamination(VISIT, DOCTOR, NOW);
        assertEquals(VisitExamStatus.IN_EXAMINATION, row.getExamStatus());
        assertEquals(NOW, row.getStartedAt());
    }

    @Test
    void markAwaitingConclusionSkipsMissingAndRepeatedRows() {
        when(queue.findById(VISIT)).thenReturn(Optional.empty());
        service.markAwaitingConclusion(VISIT, NOW);

        VisitExamQueueJpaEntity examining = waitingRow(1);
        examining.startExamination(NOW);
        existingRow(examining);
        service.markAwaitingConclusion(VISIT, NOW.plusSeconds(60));
        assertEquals(VisitExamStatus.AWAITING_CONCLUSION, examining.getExamStatus());
        assertEquals(NOW.plusSeconds(60), examining.getAwaitingAt());

        service.markAwaitingConclusion(VISIT, NOW.plusSeconds(120));
        assertEquals(NOW.plusSeconds(60), examining.getAwaitingAt());

        existingRow(waitingRow(1));
        assertConflict(() -> service.markAwaitingConclusion(VISIT, NOW));
    }

    @Test
    void completeFinishesFromBothActiveStatesOnlyForTheResponsibleDoctor() {
        VisitExamQueueJpaEntity awaiting = waitingRow(1);
        awaiting.startExamination(NOW);
        awaiting.awaitConclusion(NOW);
        existingRow(awaiting);
        assertConflict(() -> service.complete(VISIT, OTHER_DOCTOR, NOW.plusSeconds(60)));

        service.complete(VISIT, DOCTOR, NOW.plusSeconds(60));
        assertEquals(VisitExamStatus.COMPLETED, awaiting.getExamStatus());
        assertEquals(NOW.plusSeconds(60), awaiting.getFinishedAt());

        existingRow(waitingRow(2));
        assertConflict(() -> service.complete(VISIT, DOCTOR, NOW));
        when(queue.findById(VISIT)).thenReturn(Optional.empty());
        assertConflict(() -> service.complete(VISIT, DOCTOR, NOW));
    }

    @Test
    void cancelSkipsMissingRowsAndRejectsVisitsAlreadyUnderExam() {
        when(queue.findById(VISIT)).thenReturn(Optional.empty());
        service.cancel(VISIT, NOW);

        VisitExamQueueJpaEntity waiting = waitingRow(1);
        existingRow(waiting);
        service.cancel(VISIT, NOW);
        assertEquals(VisitExamStatus.CANCELLED, waiting.getExamStatus());

        VisitExamQueueJpaEntity examining = waitingRow(2);
        examining.startExamination(NOW);
        existingRow(examining);
        assertConflict(() -> service.cancel(VISIT, NOW));
    }

    private void existingRow(VisitExamQueueJpaEntity row) {
        when(queue.findById(VISIT)).thenReturn(Optional.of(row));
    }

    private static VisitExamQueueJpaEntity waitingRow(int number) {
        return VisitExamQueueJpaEntity.waiting(VISIT, DOCTOR, ROOM, null, CLINIC_DAY, number, NOW.minusSeconds(600));
    }

    private void doctorRoomAt(UUID roomId) {
        when(schedules.forProfile(DOCTOR, NOW, NOW.plusSeconds(1))).thenReturn(List.of(
                new DoctorWorkShift(UUID.randomUUID(), DOCTOR, NOW.minusSeconds(3600), NOW.plusSeconds(3600), roomId)));
    }

    private void roomLabel(UUID roomId, String label) {
        VisitRoomJpaEntity room = mock(VisitRoomJpaEntity.class);
        when(room.getDisplayLabel()).thenReturn(label);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
    }

    private static void assertConflict(Executable action) {
        VisitOperationException failure = assertThrows(VisitOperationException.class, action);
        assertEquals("VISIT_EXAM_STATE_CONFLICT", failure.code());
    }
}
