package fu.fa26.sep490.g58.otcms_be.appointment;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.*;

import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.*;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.*;

import fu.fa26.sep490.g58.otcms_be.appointment.service.*;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.*;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateAppointmentServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID PROFILE = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();
    private static final UUID DOCTOR = UUID.randomUUID();
    private CoordinationGuardService guard;
    private PatientProfileDirectory currentProfile;
    private AuthorizePatientProfileService authorization;
    private ClinicServiceBookingQueryService services;
    private StaffAvailabilityProviderQueryService providers;
    private ServiceDoctorAssignmentQueryService assignments;
    private GenerateAvailableSlotsService slots;
    private AppointmentJpaRepository appointments;
    private CreateAppointmentServiceImpl handler;

    @BeforeEach
    void setUp() {
        guard = mock(CoordinationGuardService.class);
        currentProfile = mock(PatientProfileDirectory.class);
        authorization = mock(AuthorizePatientProfileService.class);
        services = mock(ClinicServiceBookingQueryService.class);
        providers = mock(StaffAvailabilityProviderQueryService.class);
        assignments = mock(ServiceDoctorAssignmentQueryService.class);
        slots = mock(GenerateAvailableSlotsService.class);
        appointments = mock(AppointmentJpaRepository.class);
        var properties = new AppointmentBookingProperties(60, 30, 60, 15, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(30, 60));
        handler = new CreateAppointmentServiceImpl(guard, currentProfile, authorization, services, providers,
                assignments, slots, appointments, properties, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    @Test
    void patientCreationDerivesCurrentProfileAndSourceAndAcceptsZeroBuffer() {
        Instant start = Instant.parse("2026-10-16T03:00:00Z");
        when(currentProfile.findPatientProfile(ACCOUNT)).thenReturn(Optional.of(PROFILE));
        when(authorization.authorize(ACCOUNT, PROFILE)).thenReturn(new AuthorizePatientProfileService.AuthorizedPatientProfile(
                PROFILE, AuthorizePatientProfileService.AuthorizationScope.SELF));
        when(services.findActiveService(SERVICE, ClinicServiceBookingQueryService.BookingAudience.PUBLIC_PATIENT))
                .thenReturn(new ClinicServiceBookingQueryService.BookableService(SERVICE, "Visit", Duration.ofMinutes(30), Duration.ZERO, true));
        when(providers.findAcceptingDoctorsInActiveDirectory()).thenReturn(List.of(
                new StaffAvailabilityProviderQueryService.AvailabilityProvider(DOCTOR, "Doctor", true)));
        when(assignments.findActiveDoctorProfileIds(SERVICE)).thenReturn(List.of(DOCTOR));
        when(slots.generate(any())).thenReturn(List.of(new AvailableSlot(SERVICE, DOCTOR, start, start.plusSeconds(1800), start.plusSeconds(1800))));
        when(appointments.save(any(fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking.class))).thenAnswer(call -> {
            var booking = call.getArgument(0, fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking.class);
            assertEquals(PROFILE, booking.patientProfileId());
            assertEquals(fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking.BookingSource.PATIENT_ONLINE,
                    booking.bookingSource());
            assertEquals(fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking.ActorType.PATIENT,
                    booking.actorType());
            assertEquals(0, booking.bufferMinutes());
            return new CreatedAppointment(booking.id(), PROFILE, SERVICE, DOCTOR, "CONFIRMED", "PATIENT_ONLINE",
                    booking.purpose(), start, start.plusSeconds(1800), 30, 0, start.plusSeconds(1800), null, null, NOW, NOW);
        });

        CreatedAppointment created = handler.create(new CreateAppointmentCommand(ACCOUNT,
                CreateAppointmentCommand.Actor.PATIENT, null, SERVICE, DOCTOR,
                OffsetDateTime.parse("2026-10-16T10:00:00+07:00"), "Routine visit", "test-correlation"));

        assertEquals("PATIENT_ONLINE", created.bookingSource());
        verify(guard).acquire();
        verify(appointments).save(any(fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking.class));
    }

    @Test
    void patientCannotSelectAnotherProfile() {
        UUID other = UUID.randomUUID();
        when(authorization.authorize(ACCOUNT, other)).thenThrow(new PatientAuthorizationDeniedException());
        var failure = assertThrows(AppointmentBookingException.class, () -> handler.create(new CreateAppointmentCommand(
                ACCOUNT, CreateAppointmentCommand.Actor.PATIENT, other, SERVICE, DOCTOR,
                OffsetDateTime.parse("2026-10-16T10:00:00+07:00"), "Routine visit", "test-correlation")));
        assertEquals("PATIENT_PROFILE_NOT_FOUND", failure.code());
        verify(guard).acquire();
        verifyNoInteractions(appointments, services, slots);
    }

    @Test
    void pastStartIsRejectedBeforeReadingServiceOrWriting() {
        when(currentProfile.findPatientProfile(ACCOUNT)).thenReturn(Optional.of(PROFILE));
        when(authorization.authorize(ACCOUNT, PROFILE)).thenReturn(new AuthorizePatientProfileService.AuthorizedPatientProfile(
                PROFILE, AuthorizePatientProfileService.AuthorizationScope.SELF));
        var failure = assertThrows(AppointmentBookingException.class, () -> handler.create(new CreateAppointmentCommand(
                ACCOUNT, CreateAppointmentCommand.Actor.PATIENT, null, SERVICE, DOCTOR,
                OffsetDateTime.parse("2026-10-14T11:00:00+07:00"), "Routine visit", "test-correlation")));
        assertEquals("BOOKING_TIME_IN_PAST", failure.code());
        verify(guard).acquire();
        verifyNoInteractions(services, providers, assignments, slots, appointments);
    }

    @Test
    void receptionistCanCreateAtExactNowBoundaryWhenSlotIsAvailable() {
        var properties = new AppointmentBookingProperties(60, 30, 60, 15, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(30, 60));
        handler = new CreateAppointmentServiceImpl(guard, currentProfile, authorization, services, providers,
                assignments, slots, appointments, properties, Clock.fixed(NOW, ZoneId.of("UTC")));
        when(services.findActiveService(SERVICE,
                ClinicServiceBookingQueryService.BookingAudience.RECEPTIONIST))
                .thenReturn(new ClinicServiceBookingQueryService.BookableService(SERVICE, "Visit",
                        Duration.ofMinutes(30), Duration.ZERO, true));
        when(providers.findAcceptingDoctorsInActiveDirectory()).thenReturn(List.of(
                new StaffAvailabilityProviderQueryService.AvailabilityProvider(DOCTOR, "Doctor", false)));
        when(assignments.findActiveDoctorProfileIds(SERVICE)).thenReturn(List.of(DOCTOR));
        when(slots.generate(any())).thenReturn(List.of(new AvailableSlot(SERVICE, DOCTOR, NOW,
                NOW.plusSeconds(1800), NOW.plusSeconds(1800))));
        var expected = new CreatedAppointment(UUID.randomUUID(), PROFILE, SERVICE, DOCTOR, "CONFIRMED",
                "RECEPTIONIST_ASSISTED", "Routine visit", NOW, NOW.plusSeconds(1800), 30, 0,
                NOW.plusSeconds(1800), null, null, NOW, NOW);
        when(appointments.save(any(AppointmentBooking.class))).thenReturn(expected);

        var created = handler.create(new CreateAppointmentCommand(ACCOUNT,
                CreateAppointmentCommand.Actor.RECEPTIONIST, PROFILE, SERVICE, DOCTOR,
                OffsetDateTime.ofInstant(NOW, ZoneId.of("UTC")), "Routine visit", "test-correlation"));

        assertEquals(NOW, created.startTime());
        var saved = org.mockito.ArgumentCaptor.forClass(AppointmentBooking.class);
        verify(appointments).save(saved.capture());
        assertEquals(NOW, saved.getValue().startTime());
    }
}
