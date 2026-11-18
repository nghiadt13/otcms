package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderQueueService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderQueueService.QueueOrder;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoom;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ServiceQueueRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.StaffRoomScopeServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UC-64: rooms per role, and MSG-50 for a room outside the actor's scope. */
class StaffRoomScopeServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-17T02:00:00Z");
    private final ManageVisitRoomService manage = mock(ManageVisitRoomService.class);
    private final DoctorScheduleService schedules = mock(DoctorScheduleService.class);
    private final DoctorProfileIdentityQueryService doctors = mock(DoctorProfileIdentityQueryService.class);
    private final ServiceQueueRepository queue = mock(ServiceQueueRepository.class);
    private final ClinicalServiceOrderQueueService orders = mock(ClinicalServiceOrderQueueService.class);
    private final ImagingRoomQueueService imagingRooms = mock(ImagingRoomQueueService.class);
    private final AppointmentBookingProperties booking = mock(AppointmentBookingProperties.class);
    private final StaffRoomScopeServiceImpl service = new StaffRoomScopeServiceImpl(manage, schedules, doctors, queue,
            orders, imagingRooms, booking, Clock.fixed(NOW, ZoneId.of("UTC")));
    private final VisitRoom exam = room("E-1", "EXAMINATION", true), imaging = room("I-1", "IMAGING", true),
            idleImaging = room("I-2", "IMAGING", true), lab = room("L-1", "LABORATORY", true), closed = room("X-1", "EXAMINATION", false);

    @BeforeEach
    void setup() {
        when(booking.clinicTimeZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(manage.listAll()).thenReturn(List.of(exam, imaging, idleImaging, lab, closed));
    }

    private static VisitRoom room(String code, String type, boolean active) {
        return new VisitRoom(UUID.randomUUID(), code, code, type, "", List.of(), List.of(), active, NOW, NOW);
    }

    private static AuthenticatedAccount actor(AccountRole role) {
        return new AuthenticatedAccount(UUID.randomUUID(), role, UUID.randomUUID());
    }

    @Test
    void adminSeesEverythingAndReceptionistOnlyActiveRooms() {
        assertEquals(5, service.listVisible(actor(AccountRole.ADMIN)).size());
        assertEquals(List.of(exam, imaging, idleImaging, lab), service.listVisible(actor(AccountRole.RECEPTIONIST)));
    }

    @Test
    void doctorSeesShiftRoomsAndRoomsOfOwnVisitOrders() {
        AuthenticatedAccount doctor = actor(AccountRole.DOCTOR);
        UUID profile = UUID.randomUUID(), visit = UUID.randomUUID();
        when(schedules.forAccount(any(), any(), any())).thenReturn(List.of(
                new DoctorWorkShift(UUID.randomUUID(), profile, NOW, NOW.plusSeconds(3600), exam.id())));
        when(doctors.findDoctorProfileId(doctor.accountId())).thenReturn(Optional.of(profile));
        when(queue.findOpenExamVisitIds(profile)).thenReturn(List.of(visit));
        when(orders.findByVisit(visit)).thenReturn(List.of(new QueueOrder(UUID.randomUUID(), "O-1", visit, UUID.randomUUID(),
                UUID.randomUUID(), "X-ray", "IMAGING", null, null, null, profile, "ORDERED", "PAID", imaging.id(), "PENDING", NOW)));
        assertEquals(List.of(exam, imaging), service.listVisible(doctor));
    }

    @Test
    void imagingTechnicianSeesOnlyAssignedRooms() {
        AuthenticatedAccount technician = actor(AccountRole.IMAGING_TECHNICIAN);
        when(imagingRooms.isAssignedTechnician(technician.accountId(), imaging.id())).thenReturn(true);
        assertEquals(List.of(imaging), service.listVisible(technician));
        assertEquals(imaging, service.getVisible(technician, imaging.id()));
        assertEquals("MSG-50", assertThrows(VisitOperationException.class,
                () -> service.getVisible(technician, idleImaging.id())).code());
    }

    @Test
    void unknownRoomAndPatientRole() {
        assertEquals("MSG-50", assertThrows(VisitOperationException.class,
                () -> service.getVisible(actor(AccountRole.ADMIN), UUID.randomUUID())).code());
        assertEquals("MSG-40", assertThrows(VisitOperationException.class,
                () -> service.listVisible(actor(AccountRole.PATIENT))).code());
    }
}
