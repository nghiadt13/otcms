package fu.fa26.sep490.g58.otcms_be.appointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.*;

import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.*;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.*;

import fu.fa26.sep490.g58.otcms_be.appointment.service.*;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.*;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReassignAppointmentServiceImplT026Test {
    private final UUID appointmentId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID currentDoctor = UUID.randomUUID();
    private final UUID replacementDoctor = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-14T05:00:00Z");
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final AppointmentReassignmentRepository appointments = mock(AppointmentReassignmentRepository.class);
    private final ServiceDoctorAssignmentQueryService assignments = mock(ServiceDoctorAssignmentQueryService.class);
    private final StaffAvailabilityProviderQueryService providers = mock(StaffAvailabilityProviderQueryService.class);
    private final GenerateAvailableSlotsService slots = mock(GenerateAvailableSlotsService.class);
    private ReassignAppointmentServiceImpl handler;
    private ReassignableAppointment current;

    @BeforeEach
    void setUp() {
        var properties = new AppointmentBookingProperties(30, 120, 1440, 30, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(60, 60));
        handler = new ReassignAppointmentServiceImpl(guard, appointments, assignments, providers, slots, properties,
                Clock.fixed(now, ZoneId.of("UTC")));
        current = new ReassignableAppointment(appointmentId, patientId, serviceId,
                currentDoctor, "CONFIRMED", "RECEPTIONIST_ASSISTED", "knee pain", now,
                now.plusSeconds(1800), 30, 5, null, null, now.minusSeconds(86400));
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        when(providers.findAcceptingDoctorsInActiveDirectory()).thenReturn(List.of(
                new StaffAvailabilityProviderQueryService.AvailabilityProvider(replacementDoctor, "Dr Replacement", false)));
        when(assignments.findActiveDoctorProfileIds(serviceId)).thenReturn(List.of(currentDoctor, replacementDoctor));
        when(slots.generate(any())).thenReturn(List.of(new AvailableSlot(serviceId, replacementDoctor, now,
                now.plusSeconds(1800), now.plusSeconds(2100))));
    }

    @Test
    void receptionistReassignsAtExactNowBoundaryAndKeepsSameSlotSnapshot() {
        var expected = new CreatedAppointment(appointmentId, patientId, serviceId, replacementDoctor, "CONFIRMED",
                "RECEPTIONIST_ASSISTED", "knee pain", now, now.plusSeconds(1800), 30, 5,
                now.plusSeconds(2100), null, null, current.createdAt(), now);
        String correlationId = UUID.randomUUID().toString();
        when(appointments.reassign(current, replacementDoctor, accountId, "Doctor unavailable", now, correlationId))
                .thenReturn(expected);

        var result = handler.reassign(appointmentId, new ReassignAppointmentCommand(accountId, replacementDoctor,
                " Doctor unavailable ", correlationId));

        assertEquals(expected, result);
        InOrder order = inOrder(guard, appointments);
        order.verify(guard).acquire();
        order.verify(appointments).findForUpdate(appointmentId);
        verify(providers).findAcceptingDoctorsInActiveDirectory();
        verify(assignments).findActiveDoctorProfileIds(serviceId);
        var request = org.mockito.ArgumentCaptor.forClass(SlotAvailabilityRequest.class);
        verify(slots).generate(request.capture());
        assertEquals(Set.of(replacementDoctor), request.getValue().doctorProfileIds());
        assertEquals(appointmentId, request.getValue().serviceContext().excludedAppointmentId());
        assertEquals(patientId, request.getValue().serviceContext().patientProfileId());
        assertEquals(30, request.getValue().serviceContext().consultationDuration().toMinutes());
        assertEquals(5, request.getValue().serviceContext().bufferDuration().toMinutes());
        verify(appointments).reassign(current, replacementDoctor, accountId, "Doctor unavailable", now, correlationId);
    }

    @Test
    void rejectsPastSameDoctorUnavailableDoctorAndNonConfirmedWithoutMutation() {
        var past = new ReassignableAppointment(appointmentId, patientId, serviceId,
                currentDoctor, "CONFIRMED", "RECEPTIONIST_ASSISTED", "knee pain", now.minusSeconds(1),
                now.plusSeconds(1799), 30, 5, null, null, current.createdAt());
        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(past));
        assertEquals("BOOKING_TIME_IN_PAST", assertThrows(AppointmentBookingException.class,
                () -> handler.reassign(appointmentId, command(replacementDoctor))).code());
        verifyNoInteractions(providers, assignments, slots);

        when(appointments.findForUpdate(appointmentId)).thenReturn(Optional.of(current));
        assertEquals("VALIDATION_FAILED", assertThrows(AppointmentBookingException.class,
                () -> handler.reassign(appointmentId, command(currentDoctor))).code());
        when(providers.findAcceptingDoctorsInActiveDirectory()).thenReturn(List.of());
        assertEquals("DOCTOR_NOT_ELIGIBLE", assertThrows(AppointmentBookingException.class,
                () -> handler.reassign(appointmentId, command(replacementDoctor))).code());
        verify(appointments, never()).reassign(any(), any(), any(), any(), any(), any());
    }

    private ReassignAppointmentCommand command(UUID doctor) {
        return new ReassignAppointmentCommand(accountId, doctor, "Reason", "trace-id");
    }
}
