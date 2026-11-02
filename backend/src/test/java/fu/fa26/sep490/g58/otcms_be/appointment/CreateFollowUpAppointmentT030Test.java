package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentJpaRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateFollowUpAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.CreateFollowUpAppointmentServiceImpl;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitWorkflowService;
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

class CreateFollowUpAppointmentT030Test {
    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID SOURCE_VISIT = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID ORDERING_DOCTOR = UUID.randomUUID();
    private static final UUID TARGET_DOCTOR = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();
    private CoordinationGuardService guard;
    private DoctorProfileIdentityQueryService doctors;
    private VisitWorkflowService visits;
    private ClinicServiceBookingQueryService services;
    private StaffAvailabilityProviderQueryService providers;
    private ServiceDoctorAssignmentQueryService assignments;
    private GenerateAvailableSlotsService slots;
    private AppointmentJpaRepository appointments;
    private CreateFollowUpAppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        guard = mock(CoordinationGuardService.class);
        doctors = mock(DoctorProfileIdentityQueryService.class);
        visits = mock(VisitWorkflowService.class);
        services = mock(ClinicServiceBookingQueryService.class);
        providers = mock(StaffAvailabilityProviderQueryService.class);
        assignments = mock(ServiceDoctorAssignmentQueryService.class);
        slots = mock(GenerateAvailableSlotsService.class);
        appointments = mock(AppointmentJpaRepository.class);
        var properties = new AppointmentBookingProperties(60, 30, 60, 15, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(30, 60));
        service = new CreateFollowUpAppointmentServiceImpl(guard, doctors, visits, services, providers,
                assignments, slots, appointments, properties, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    @Test
    void createsFollowUpWithVisitDerivedPatientAndAuthenticatedOrderingDoctor() {
        Instant start = Instant.parse("2026-10-19T03:30:00Z");
        when(doctors.findDoctorProfileId(ACCOUNT)).thenReturn(Optional.of(ORDERING_DOCTOR));
        when(visits.requireFollowUpEvidence(SOURCE_VISIT, ORDERING_DOCTOR)).thenReturn(
                new VisitWorkflowService.FollowUpEvidence(SOURCE_VISIT, PATIENT, ORDERING_DOCTOR,
                        true, true, true, UUID.randomUUID(), NOW));
        when(services.findActiveService(SERVICE, ClinicServiceBookingQueryService.BookingAudience.DOCTOR_FOLLOW_UP))
                .thenReturn(new ClinicServiceBookingQueryService.BookableService(SERVICE, "Review", Duration.ofMinutes(30), Duration.ofMinutes(5), false));
        when(providers.findAcceptingDoctorsInActiveDirectory()).thenReturn(List.of(
                new StaffAvailabilityProviderQueryService.AvailabilityProvider(TARGET_DOCTOR, "Doctor", false)));
        when(assignments.findActiveDoctorProfileIds(SERVICE)).thenReturn(List.of(TARGET_DOCTOR));
        when(slots.generate(any())).thenReturn(List.of(new AvailableSlot(SERVICE, TARGET_DOCTOR,
                start, start.plusSeconds(1800), start.plusSeconds(2100))));
        when(appointments.saveFollowUp(any())).thenAnswer(invocation -> {
            AppointmentBooking booking = invocation.getArgument(0);
            assertEquals(PATIENT, booking.patientProfileId());
            assertEquals(TARGET_DOCTOR, booking.doctorProfileId());
            assertEquals(ORDERING_DOCTOR, booking.orderingDoctorProfileId());
            assertEquals(SOURCE_VISIT, booking.sourceVisitId());
            assertEquals("Review symptoms", booking.reason());
            assertEquals(AppointmentBooking.BookingSource.DOCTOR_FOLLOW_UP, booking.bookingSource());
            assertEquals(AppointmentBooking.ActorType.DOCTOR, booking.actorType());
            return new CreatedAppointment(booking.id(), PATIENT, SERVICE, TARGET_DOCTOR, "CONFIRMED",
                    "DOCTOR_FOLLOW_UP", booking.purpose(), start, start.plusSeconds(1800), 30, 5,
                    start.plusSeconds(2100), null, SOURCE_VISIT, NOW, NOW);
        });

        CreatedAppointment created = service.create(new CreateFollowUpAppointmentCommand(ACCOUNT, SOURCE_VISIT,
                SERVICE, TARGET_DOCTOR, OffsetDateTime.parse("2026-10-19T10:30:00+07:00"),
                "Follow-up review", "Review symptoms", "correlation-t030"));

        assertEquals(PATIENT, created.patientProfileId());
        assertEquals(SOURCE_VISIT, created.sourceVisitId());
        verify(guard).acquire();
        verify(appointments).saveFollowUp(any());
    }

    @Test
    void deniesDoctorWhoWasNotAssignedToSourceVisitWithoutReadingCatalog() {
        when(doctors.findDoctorProfileId(ACCOUNT)).thenReturn(Optional.of(ORDERING_DOCTOR));
        when(visits.requireFollowUpEvidence(SOURCE_VISIT, ORDERING_DOCTOR)).thenReturn(
                new VisitWorkflowService.FollowUpEvidence(SOURCE_VISIT, PATIENT, ORDERING_DOCTOR,
                        false, false, false, null, NOW));

        AppointmentBookingException failure = assertThrows(AppointmentBookingException.class, () -> service.create(
                new CreateFollowUpAppointmentCommand(ACCOUNT, SOURCE_VISIT, SERVICE, TARGET_DOCTOR,
                        OffsetDateTime.parse("2026-10-21T10:00:00+07:00"), "Follow-up", "Reason", "corr")));

        assertEquals("SOURCE_VISIT_NOT_FOUND", failure.code());
        verifyNoInteractions(services, providers, assignments, slots, appointments);
    }

    @Test
    void rejectsIncompleteExaminationOrMissingIndication() {
        when(doctors.findDoctorProfileId(ACCOUNT)).thenReturn(Optional.of(ORDERING_DOCTOR));
        when(visits.requireFollowUpEvidence(SOURCE_VISIT, ORDERING_DOCTOR)).thenReturn(
                new VisitWorkflowService.FollowUpEvidence(SOURCE_VISIT, PATIENT, ORDERING_DOCTOR,
                        true, true, false, null, NOW));

        AppointmentBookingException failure = assertThrows(AppointmentBookingException.class, () -> service.create(
                new CreateFollowUpAppointmentCommand(ACCOUNT, SOURCE_VISIT, SERVICE, TARGET_DOCTOR,
                        OffsetDateTime.parse("2026-10-21T10:00:00+07:00"), "Follow-up", "Reason", "corr")));

        assertEquals("FOLLOW_UP_NOT_ALLOWED", failure.code());
        verifyNoInteractions(services, providers, assignments, slots, appointments);
    }

    @Test
    void unavailableVisitEvidenceFailsClosed() {
        when(doctors.findDoctorProfileId(ACCOUNT)).thenReturn(Optional.of(ORDERING_DOCTOR));
        when(visits.requireFollowUpEvidence(SOURCE_VISIT, ORDERING_DOCTOR))
                .thenThrow(new VisitWorkflowException("DEPENDENCY_UNAVAILABLE"));

        AppointmentBookingException failure = assertThrows(AppointmentBookingException.class, () -> service.create(
                new CreateFollowUpAppointmentCommand(ACCOUNT, SOURCE_VISIT, SERVICE, TARGET_DOCTOR,
                        OffsetDateTime.parse("2026-10-21T10:00:00+07:00"), "Follow-up", "Reason", "corr")));

        assertEquals("DEPENDENCY_UNAVAILABLE", failure.code());
        verifyNoInteractions(services, providers, assignments, slots, appointments);
    }

    @Test
    void rejectsMissingRequiredReasonBeforeGuardOrOwnerReads() {
        AppointmentBookingException failure = assertThrows(AppointmentBookingException.class, () -> service.create(
                new CreateFollowUpAppointmentCommand(ACCOUNT, SOURCE_VISIT, SERVICE, TARGET_DOCTOR,
                        OffsetDateTime.parse("2026-10-21T10:00:00+07:00"), "Follow-up", " ", "corr")));

        assertEquals("VALIDATION_FAILED", failure.code());
        verifyNoInteractions(guard, doctors, visits, services, appointments);
    }
}
