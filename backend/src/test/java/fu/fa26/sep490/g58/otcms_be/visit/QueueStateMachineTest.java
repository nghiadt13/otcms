package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.QueueRoomRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.QueueStateMachineImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueueStateMachineTest {
    private static final Instant NOW = Instant.parse("2026-11-17T02:00:00Z");
    private final UUID room = UUID.randomUUID(), account = UUID.randomUUID(), doctor = UUID.randomUUID();
    private final QueueRoomRepository queue = mock(QueueRoomRepository.class);
    private final VisitRoomJpaRepository rooms = mock(VisitRoomJpaRepository.class);
    private final ReceptionistAccountAccessService receptionists = mock(ReceptionistAccountAccessService.class);
    private final AppointmentBookingProperties booking = mock(AppointmentBookingProperties.class);
    private final AuditLogWriter audit = mock(AuditLogWriter.class);
    private final AuthenticatedAccount actor = new AuthenticatedAccount(account, AccountRole.RECEPTIONIST, UUID.randomUUID());
    private final QueueStateMachineImpl machine = new QueueStateMachineImpl(queue, rooms, receptionists,
            mock(DoctorAccountAccessService.class), mock(DoctorScheduleService.class), booking, audit,
            Clock.fixed(NOW, ZoneId.of("UTC")));

    @BeforeEach
    void setup() {
        when(booking.clinicTimeZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(receptionists.isActiveReceptionistAccount(account)).thenReturn(true);
        when(rooms.findByIdForUpdate(room)).thenReturn(Optional.of(mock(VisitRoomJpaEntity.class)));
    }

    @Test
    void callsFirstWaitingAndDoesNotCallAnother() {
        QueueEntryJpaEntity first = entry(1, 0), second = entry(2, 1);
        when(queue.lockRoom(room, "EXAM")).thenReturn(List.of(first, second));
        assertEquals(first.getId(), machine.callNext(room, "EXAM", actor).id());
        assertEquals("CALLED", first.getStatus());
        assertEquals("MSG-42", assertThrows(VisitOperationException.class,
                () -> machine.callNext(room, "EXAM", actor)).code());
    }

    @Test
    void returnedPatientIsInsertedBetweenWaitingEntriesAndAudited() {
        QueueEntryJpaEntity first = entry(1, 0), second = entry(2, 4), absent = entry(3, 1);
        absent.call(NOW); absent.markAbsent(NOW);
        location(absent);
        when(queue.lockRoom(room, "EXAM")).thenReturn(List.of(first, absent, second));
        machine.returnFromAbsence(absent.getId(), second.getId(), "Patient returned", actor);
        assertTrue(first.getSortOrder() < absent.getSortOrder());
        assertTrue(absent.getSortOrder() < second.getSortOrder());
        assertEquals("WAITING", absent.getStatus());
        verify(audit).record(any());
    }

    @Test
    void rejectsMissingReasonPresentEntryAndInsertionBeforeCalled() {
        QueueEntryJpaEntity called = entry(1, 0), absent = entry(2, 1), waiting = entry(3, 2);
        called.call(NOW); absent.call(NOW); absent.markAbsent(NOW);
        location(absent); location(waiting);
        when(queue.lockRoom(room, "EXAM")).thenReturn(List.of(called, absent, waiting));
        assertEquals("MSG-39", assertThrows(VisitOperationException.class,
                () -> machine.returnFromAbsence(absent.getId(), null, "  ", actor)).code());
        assertEquals("MSG-42", assertThrows(VisitOperationException.class,
                () -> machine.returnFromAbsence(waiting.getId(), null, "reason", actor)).code());
        assertEquals("MSG-42", assertThrows(VisitOperationException.class,
                () -> machine.returnFromAbsence(absent.getId(), called.getId(), "reason", actor)).code());
        verifyNoInteractions(audit);
    }

    @Test
    void renumbersWhenAdjacentEntriesHaveNoGap() {
        QueueEntryJpaEntity first = entry(1, 0), second = entry(2, 1), absent = entry(3, 2);
        first.changeSortOrder(1); second.changeSortOrder(2);
        absent.call(NOW); absent.markAbsent(NOW);
        location(absent);
        when(queue.lockRoom(room, "EXAM")).thenReturn(List.of(first, second, absent));
        machine.returnFromAbsence(absent.getId(), second.getId(), "Patient returned", actor);
        assertTrue(first.getSortOrder() < absent.getSortOrder());
        assertTrue(absent.getSortOrder() < second.getSortOrder());
    }

    private QueueEntryJpaEntity entry(int number, int seconds) {
        return QueueEntryJpaEntity.waiting(UUID.randomUUID(), doctor, room, null,
                LocalDate.of(2026, 11, 17), number, NOW.plusSeconds(seconds));
    }

    private void location(QueueEntryJpaEntity row) {
        QueueRoomRepository.Location location = mock(QueueRoomRepository.Location.class);
        when(location.getRoomId()).thenReturn(room);
        when(location.getStage()).thenReturn("EXAM");
        when(queue.findLocation(row.getId())).thenReturn(Optional.of(location));
    }
}
