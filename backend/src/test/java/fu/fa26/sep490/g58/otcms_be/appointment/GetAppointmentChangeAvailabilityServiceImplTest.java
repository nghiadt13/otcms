package fu.fa26.sep490.g58.otcms_be.appointment;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.*;

import fu.fa26.sep490.g58.otcms_be.appointment.service.*;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.*;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentChange;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GetAppointmentChangeAvailabilityServiceImplTest {
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID currentDoctor = UUID.randomUUID();
    private final UUID replacement = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    private final AppointmentChangeRepository appointments = mock(AppointmentChangeRepository.class);
    private final AuthorizePatientProfileService auth = mock(AuthorizePatientProfileService.class);
    private final ServiceDoctorAssignmentQueryService assignments = mock(ServiceDoctorAssignmentQueryService.class);
    private final StaffAvailabilityProviderQueryService providers = mock(StaffAvailabilityProviderQueryService.class);
    private final GenerateAvailableSlotsService slots = mock(GenerateAvailableSlotsService.class);
    private GetAppointmentChangeAvailabilityServiceImpl handler;

    @BeforeEach
    void setUp() {
        var properties = new AppointmentBookingProperties(30, 120, 1440, 30, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(60, 60));
        handler = new GetAppointmentChangeAvailabilityServiceImpl(appointments, auth, assignments, providers,
                slots, properties, Clock.fixed(now, ZoneId.of("UTC")));
        when(appointments.findChangeContext(appointmentId)).thenReturn(Optional.of(new AppointmentChange(
                appointmentId, patientId, serviceId, currentDoctor, "CONFIRMED",
                now.plusSeconds(3 * 86400L), 30, 5)));
        when(assignments.findActiveDoctorProfileIds(serviceId)).thenReturn(List.of(currentDoctor, replacement));
        when(providers.findAcceptingDoctorsInActiveDirectory()).thenReturn(List.of(
                new StaffAvailabilityProviderQueryService.AvailabilityProvider(replacement, "Dr Replacement", true)));
        when(providers.findDoctorDisplayName(currentDoctor)).thenReturn(Optional.of("Dr Current"));
    }

    @Test
    void patientRescheduleAuthorizesSelfAndUsesStoredSnapshotAndSelfExclusion() {
        Instant candidate = now.plusSeconds(3 * 86400L);
        when(slots.generate(any())).thenReturn(List.of(new AvailableSlot(serviceId, currentDoctor, candidate,
                candidate.plusSeconds(1800), candidate.plusSeconds(2100))));

        var result = handler.findRescheduleSlots(appointmentId, accountId, AppointmentAvailabilityQuery.Audience.PUBLIC,
                LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17));

        assertEquals(1, result.size());
        assertEquals("Dr Current", result.getFirst().doctorDisplayName());
        verify(auth).authorize(accountId, patientId);
        var request = org.mockito.ArgumentCaptor.forClass(SlotAvailabilityRequest.class);
        verify(slots).generate(request.capture());
        assertEquals(appointmentId, request.getValue().serviceContext().excludedAppointmentId());
        assertEquals(30, request.getValue().serviceContext().consultationDuration().toMinutes());
        assertEquals(5, request.getValue().serviceContext().bufferDuration().toMinutes());
    }

    @Test
    void reassignmentOffersOnlyDifferentEligibleDoctorAtOriginalSlot() {
        Instant candidate = now.plusSeconds(3 * 86400L);
        when(slots.generate(any())).thenReturn(List.of(new AvailableSlot(serviceId, replacement, candidate,
                candidate.plusSeconds(1800), candidate.plusSeconds(2100))));

        var result = handler.findReassignmentSlots(appointmentId, LocalDate.of(2026, 10, 17),
                LocalDate.of(2026, 10, 17));

        assertEquals(1, result.size());
        assertEquals(replacement, result.getFirst().doctorId());
        assertEquals("Dr Replacement", result.getFirst().doctorDisplayName());
        verifyNoInteractions(auth);
    }

    @Test
    void rejectsPatientChangeInsideOldAppointmentDeadline() {
        when(appointments.findChangeContext(appointmentId)).thenReturn(Optional.of(new AppointmentChange(
                appointmentId, patientId, serviceId, currentDoctor, "CONFIRMED", now.plusSeconds(100), 30, 5)));
        var error = assertThrows(AppointmentAvailabilityException.class, () -> handler.findRescheduleSlots(
                appointmentId, accountId, AppointmentAvailabilityQuery.Audience.PUBLIC,
                LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17)));
        assertEquals("APPOINTMENT_CHANGE_TOO_LATE", error.code());
        verifyNoInteractions(slots);
    }

    @Test
    void hidesAppointmentStateFromPatientWithoutCurrentSelfAuthorization() {
        when(appointments.findChangeContext(appointmentId)).thenReturn(Optional.of(new AppointmentChange(
                appointmentId, patientId, serviceId, currentDoctor, "CHECKED_IN",
                now.plusSeconds(86400), 30, 5)));
        when(auth.authorize(accountId, patientId)).thenThrow(new PatientAuthorizationDeniedException());
        var error = assertThrows(AppointmentAvailabilityException.class, () -> handler.findRescheduleSlots(
                appointmentId, accountId, AppointmentAvailabilityQuery.Audience.PUBLIC,
                LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17)));
        assertEquals("APPOINTMENT_NOT_FOUND", error.code());
    }

    @Test
    void rejectsPastAppointmentReassignmentAndNonConfirmedAppointments() {
        when(appointments.findChangeContext(appointmentId)).thenReturn(Optional.of(new AppointmentChange(
                appointmentId, patientId, serviceId, currentDoctor, "CONFIRMED", now.minusSeconds(1), 30, 5)));
        assertEquals("BOOKING_TIME_IN_PAST", assertThrows(AppointmentAvailabilityException.class,
                () -> handler.findReassignmentSlots(appointmentId, LocalDate.of(2026, 10, 17),
                        LocalDate.of(2026, 10, 17))).code());
        when(appointments.findChangeContext(appointmentId)).thenReturn(Optional.of(new AppointmentChange(
                appointmentId, patientId, serviceId, currentDoctor, "CHECKED_IN", now.plusSeconds(86400), 30, 5)));
        assertEquals("APPOINTMENT_NOT_CHANGEABLE", assertThrows(AppointmentAvailabilityException.class,
                () -> handler.findRescheduleSlots(appointmentId, accountId, AppointmentAvailabilityQuery.Audience.PUBLIC,
                        LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17))).code());
    }
}
