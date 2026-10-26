package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentAvailabilityException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentAvailabilityQuery;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.GetAppointmentAvailabilityServiceImpl;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetAppointmentAvailabilityServiceImplTest {
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID PUBLIC_DOCTOR = UUID.randomUUID();
    private static final UUID HIDDEN_DOCTOR = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-15T05:00:00Z");
    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 10, 15);

    private final ClinicServiceBookingQueryService services = mock(ClinicServiceBookingQueryService.class);
    private final StaffAvailabilityProviderQueryService providers = mock(StaffAvailabilityProviderQueryService.class);
    private final ServiceDoctorAssignmentQueryService assignments = mock(ServiceDoctorAssignmentQueryService.class);
    private final GenerateAvailableSlotsService slotEngine = mock(GenerateAvailableSlotsService.class);
    private GetAppointmentAvailabilityServiceImpl handler;

    @BeforeEach
    void setUp() {
        var properties = new AppointmentBookingProperties(30, 120, 1440, 30, 15, 15,
                ZoneId.of("Asia/Ho_Chi_Minh"), new AppointmentBookingProperties.PublicRateLimit(60, 60));
        handler = new GetAppointmentAvailabilityServiceImpl(services, providers, assignments, slotEngine,
                properties, Clock.fixed(NOW, ZoneId.of("UTC")));
        when(services.findActiveService(SERVICE_ID, ClinicServiceBookingQueryService.BookingAudience.PUBLIC_PATIENT))
                .thenReturn(new ClinicServiceBookingQueryService.BookableService(SERVICE_ID, "Service", Duration.ofMinutes(30),
                        Duration.ofMinutes(5), true));
        when(services.findActiveService(SERVICE_ID, ClinicServiceBookingQueryService.BookingAudience.RECEPTIONIST))
                .thenReturn(new ClinicServiceBookingQueryService.BookableService(SERVICE_ID, "Service", Duration.ofMinutes(30),
                        Duration.ofMinutes(5), true));
        when(providers.findAcceptingDoctorsInActiveDirectory()).thenReturn(List.of(
                new StaffAvailabilityProviderQueryService.AvailabilityProvider(PUBLIC_DOCTOR, "Public Doctor", true),
                new StaffAvailabilityProviderQueryService.AvailabilityProvider(HIDDEN_DOCTOR, "Hidden Doctor", false)));
        when(assignments.findActiveDoctorProfileIds(SERVICE_ID)).thenReturn(List.of(PUBLIC_DOCTOR, HIDDEN_DOCTOR));
        when(slotEngine.generate(any())).thenReturn(List.of(
                slot(PUBLIC_DOCTOR, Instant.parse("2026-10-15T06:45:00Z")),
                slot(PUBLIC_DOCTOR, Instant.parse("2026-10-15T07:00:00Z")),
                slot(HIDDEN_DOCTOR, Instant.parse("2026-10-15T07:00:00Z"))));
    }

    @Test
    void publicAudienceRequiresVisibilityAndConfiguredOnlineNotice() {
        var result = handler.find(query(AppointmentAvailabilityQuery.Audience.PUBLIC, null));

        assertEquals(1, result.size());
        assertEquals(PUBLIC_DOCTOR, result.getFirst().doctorId());
        assertEquals("Public Doctor", result.getFirst().doctorDisplayName());
        assertEquals(Instant.parse("2026-10-15T07:00:00Z"), result.getFirst().startTime());
    }

    @Test
    void staffAudienceCanInspectHiddenDoctorAndAcceptsStartAtNow() {
        when(slotEngine.generate(any())).thenReturn(List.of(slot(HIDDEN_DOCTOR, NOW)));

        var result = handler.find(query(AppointmentAvailabilityQuery.Audience.STAFF, HIDDEN_DOCTOR));

        assertEquals(1, result.size());
        assertEquals(HIDDEN_DOCTOR, result.getFirst().doctorId());
        assertEquals(NOW, result.getFirst().startTime());
    }

    @Test
    void rejectsExplicitDoctorOutsideEligibleAssignmentIntersection() {
        UUID unassignedDoctor = UUID.randomUUID();

        var exception = assertThrows(AppointmentAvailabilityException.class,
                () -> handler.find(query(AppointmentAvailabilityQuery.Audience.STAFF, unassignedDoctor)));

        assertEquals("DOCTOR_NOT_ELIGIBLE", exception.code());
        verify(slotEngine, never()).generate(any());
    }

    @Test
    void returnsEmptyWhenServiceHasNoEligibleDoctor() {
        when(assignments.findActiveDoctorProfileIds(SERVICE_ID)).thenReturn(List.of());

        assertEquals(List.of(), handler.find(query(AppointmentAvailabilityQuery.Audience.STAFF, null)));
        verify(slotEngine, never()).generate(any());
    }

    @Test
    void rejectsReversedOversizedAndOutsideHorizonDateWindows() {
        var reversed = new AppointmentAvailabilityQuery(SERVICE_ID, REQUEST_DATE.plusDays(1), REQUEST_DATE,
                null, AppointmentAvailabilityQuery.Audience.PUBLIC);
        var oversized = new AppointmentAvailabilityQuery(SERVICE_ID, REQUEST_DATE, REQUEST_DATE.plusDays(31),
                null, AppointmentAvailabilityQuery.Audience.PUBLIC);
        var outsideHorizon = new AppointmentAvailabilityQuery(SERVICE_ID, REQUEST_DATE.minusDays(2), REQUEST_DATE,
                null, AppointmentAvailabilityQuery.Audience.PUBLIC);

        assertEquals("VALIDATION_FAILED", assertThrows(AppointmentAvailabilityException.class,
                () -> handler.find(reversed)).code());
        assertEquals("VALIDATION_FAILED", assertThrows(AppointmentAvailabilityException.class,
                () -> handler.find(oversized)).code());
        assertEquals("BOOKING_WINDOW_VIOLATION", assertThrows(AppointmentAvailabilityException.class,
                () -> handler.find(outsideHorizon)).code());
    }

    @Test
    void acceptsTheLastDateInTheConfiguredHorizonAndRejectsTheNextDate() {
        LocalDate lastAllowedDate = REQUEST_DATE.plusDays(30);
        var lastAllowed = new AppointmentAvailabilityQuery(SERVICE_ID, lastAllowedDate, lastAllowedDate,
                null, AppointmentAvailabilityQuery.Audience.STAFF);

        when(slotEngine.generate(any())).thenReturn(List.of());
        assertEquals(List.of(), handler.find(lastAllowed));
        verify(slotEngine).generate(any());

        LocalDate firstDisallowedDate = lastAllowedDate.plusDays(1);
        var outsideHorizon = new AppointmentAvailabilityQuery(SERVICE_ID, firstDisallowedDate,
                firstDisallowedDate, null, AppointmentAvailabilityQuery.Audience.STAFF);
        var failure = assertThrows(AppointmentAvailabilityException.class, () -> handler.find(outsideHorizon));

        assertEquals("BOOKING_WINDOW_VIOLATION", failure.code());
        verify(slotEngine, org.mockito.Mockito.times(1)).generate(any());
    }

    private AppointmentAvailabilityQuery query(AppointmentAvailabilityQuery.Audience audience, UUID doctorId) {
        return new AppointmentAvailabilityQuery(SERVICE_ID, REQUEST_DATE, REQUEST_DATE, doctorId, audience);
    }

    private AvailableSlot slot(UUID doctorId, Instant startsAt) {
        return new AvailableSlot(SERVICE_ID, doctorId, startsAt, startsAt.plus(Duration.ofMinutes(30)),
                startsAt.plus(Duration.ofMinutes(35)));
    }
}
