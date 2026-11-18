package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService;
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

/** UC-46: the technician assigned to a room calls patients from its SERVICE queue, and only from there. */
class ServiceQueueTechnicianAccessTest {
    private static final Instant NOW = Instant.parse("2026-11-17T02:00:00Z");
    private final UUID room = UUID.randomUUID(), account = UUID.randomUUID();
    private final QueueRoomRepository queue = mock(QueueRoomRepository.class);
    private final VisitRoomJpaRepository rooms = mock(VisitRoomJpaRepository.class);
    private final AppointmentBookingProperties booking = mock(AppointmentBookingProperties.class);
    private final ImagingRoomQueueService imagingRooms = mock(ImagingRoomQueueService.class);
    private final AuthenticatedAccount technician = new AuthenticatedAccount(account, AccountRole.IMAGING_TECHNICIAN, UUID.randomUUID());
    private final QueueStateMachineImpl machine = new QueueStateMachineImpl(queue, rooms,
            mock(ReceptionistAccountAccessService.class), mock(DoctorAccountAccessService.class),
            mock(DoctorScheduleService.class), booking, mock(AuditLogWriter.class), Clock.fixed(NOW, ZoneId.of("UTC")),
            imagingRooms);

    @BeforeEach
    void setup() {
        when(booking.clinicTimeZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(rooms.findByIdForUpdate(room)).thenReturn(Optional.of(mock(VisitRoomJpaEntity.class)));
    }

    @Test
    void assignedTechnicianCallsAndMarksAbsentInTheServiceQueue() {
        when(imagingRooms.isAssignedTechnician(account, room)).thenReturn(true);
        QueueEntryJpaEntity entry = QueueEntryJpaEntity.service(UUID.randomUUID(), UUID.randomUUID(), room, null,
                LocalDate.of(2026, 11, 17), 1, NOW.toEpochMilli(), NOW);
        when(queue.lockRoom(room, "SERVICE")).thenReturn(List.of(entry));
        QueueRoomRepository.Location location = mock(QueueRoomRepository.Location.class);
        when(location.getRoomId()).thenReturn(room);
        when(location.getStage()).thenReturn("SERVICE");
        when(queue.findLocation(entry.getId())).thenReturn(Optional.of(location));

        assertEquals("CALLED", machine.callNext(room, "SERVICE", technician).status());
        assertEquals("ABSENT", machine.markAbsent(entry.getId(), technician).status());
    }

    @Test
    void technicianOfAnotherRoomIsDenied() {
        when(imagingRooms.isAssignedTechnician(any(), any())).thenReturn(false);
        assertEquals("MSG-40", assertThrows(VisitOperationException.class,
                () -> machine.callNext(room, "SERVICE", technician)).code());
    }

    @Test
    void technicianCannotRunTheDoctorsExamQueueEvenInTheirOwnRoom() {
        when(imagingRooms.isAssignedTechnician(account, room)).thenReturn(true);
        assertEquals("MSG-40", assertThrows(VisitOperationException.class,
                () -> machine.callNext(room, "EXAM", technician)).code());
    }
}
