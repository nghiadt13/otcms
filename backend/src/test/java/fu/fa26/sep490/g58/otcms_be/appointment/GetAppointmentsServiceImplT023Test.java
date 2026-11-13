package fu.fa26.sep490.g58.otcms_be.appointment;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffDirectoryQueryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.*;

import fu.fa26.sep490.g58.otcms_be.appointment.service.*;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.*;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.appointment.model.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetAppointmentsServiceImplT023Test {
    private final AppointmentQueryRepository queries = mock(AppointmentQueryRepository.class);
    private final PatientProfileDirectory patients = mock(PatientProfileDirectory.class);
    private final PatientProfileAccessService patientAccess = mock(PatientProfileAccessService.class);
    private final StaffDirectoryQueryService staff = mock(StaffDirectoryQueryService.class);
    private final DoctorAccountAccessService doctorAccess = mock(DoctorAccountAccessService.class);
    private final StaffAvailabilityProviderQueryService doctors = mock(StaffAvailabilityProviderQueryService.class);
    private final ClinicServiceBookingQueryService services = mock(ClinicServiceBookingQueryService.class);
    private final UUID account = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID(), appointment = UUID.randomUUID();
    private GetAppointmentsServiceImpl handler;

    @BeforeEach void setup() { handler = new GetAppointmentsServiceImpl(queries, patients, patientAccess, staff, doctorAccess,
            doctors, services, Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC)); }

    @Test void detailCarriesDoctorNameAndServiceNameAndPrice() {
        var service = UUID.randomUUID();
        var start = Instant.parse("2026-10-21T03:00:00Z");
        when(patientAccess.accessibleProfileIds(account)).thenReturn(Set.of(patient));
        when(queries.findScoped(any(), eq(appointment))).thenReturn(Optional.of(new Row(appointment, patient, service,
                doctor, "CONFIRMED", "PATIENT_ONLINE", "Đau đầu", start, start.plusSeconds(1800), 30, 10,
                start.plusSeconds(2400), null, null, start, start)));
        when(doctors.findDoctorDisplayName(doctor)).thenReturn(Optional.of("BS. Nguyễn Minh Quân"));
        when(services.findServiceLabel(service)).thenReturn(Optional.of(
                new ClinicServiceBookingQueryService.ServiceLabel("Khám tổng quát", new java.math.BigDecimal("200000"))));

        var result = handler.detail(account, AccountRole.PATIENT, appointment);

        assertEquals("BS. Nguyễn Minh Quân", result.doctorDisplayName());
        assertEquals("Khám tổng quát", result.serviceName());
        assertEquals(new java.math.BigDecimal("200000"), result.servicePrice());
    }

    @Test void namesAreNullWhenDoctorOrServiceNoLongerExists() {
        var start = Instant.parse("2026-10-21T03:00:00Z");
        when(patientAccess.accessibleProfileIds(account)).thenReturn(Set.of(patient));
        when(queries.findScoped(any(), eq(appointment))).thenReturn(Optional.of(new Row(appointment, patient,
                UUID.randomUUID(), doctor, "CONFIRMED", "PATIENT_ONLINE", "x", start, start.plusSeconds(1800), 30, 10,
                start.plusSeconds(2400), null, null, start, start)));
        when(doctors.findDoctorDisplayName(any())).thenReturn(Optional.empty());
        when(services.findServiceLabel(any())).thenReturn(Optional.empty());

        var result = handler.detail(account, AccountRole.PATIENT, appointment);

        assertNull(result.doctorDisplayName());
        assertNull(result.serviceName());
        assertNull(result.servicePrice());
    }

    @Test void patientListUsesCurrentSelfAuthorizationAndConstrainedScope() {
        when(patientAccess.accessibleProfileIds(account)).thenReturn(Set.of(patient));
        var repositoryPage = new Page(List.of(), 0, 20, 0, 0);
        when(queries.find(argThat(scope -> scope.type() == ScopeType.PATIENT && Set.of(patient).equals(scope.profileIds())),
                any(Filter.class), eq(0), eq(20))).thenReturn(repositoryPage);
        var result = handler.list(account, AccountRole.PATIENT,
                new AppointmentSearchFilter(null, null, null, null, null), 0, 20);
        assertEquals(List.of(), result.items());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
        verify(patientAccess).accessibleProfileIds(account);
    }

    @Test void patientListCoversSelfAndConfirmedDependents() {
        var dependent = UUID.randomUUID();
        when(patientAccess.accessibleProfileIds(account)).thenReturn(Set.of(patient, dependent));
        when(queries.find(argThat(scope -> scope.type() == ScopeType.PATIENT
                && Set.of(patient, dependent).equals(scope.profileIds())), any(Filter.class), eq(0), eq(20)))
                .thenReturn(new Page(List.of(), 0, 20, 0, 0));
        var result = handler.list(account, AccountRole.PATIENT,
                new AppointmentSearchFilter(null, null, null, null, dependent), 0, 20);
        assertEquals(List.of(), result.items());
        verify(queries).find(any(), argThat(filter -> dependent.equals(filter.patientProfileId())), eq(0), eq(20));
    }

    @Test void missingCurrentPatientProfileCannotExpandScope() {
        when(patientAccess.accessibleProfileIds(account)).thenReturn(Set.of());
        var result = handler.list(account, AccountRole.PATIENT,
                new AppointmentSearchFilter(null, null, null, null, null), 0, 10);
        assertEquals(List.of(), result.items());
        assertEquals(0, result.page());
        assertEquals(10, result.size());
        assertEquals(0, result.totalElements());
        assertEquals(0, result.totalPages());
        verifyNoInteractions(queries);
    }

    @Test void detailReturnsSameNotFoundForMissingOrOtherPatientsAppointment() {
        when(patientAccess.accessibleProfileIds(account)).thenReturn(Set.of(patient));
        when(queries.findScoped(argThat(scope -> scope.type() == ScopeType.PATIENT && Set.of(patient).equals(scope.profileIds())), eq(appointment)))
                .thenReturn(Optional.empty());
        var error = assertThrows(AppointmentQueryException.class, () -> handler.detail(account, AccountRole.PATIENT, appointment));
        assertEquals("APPOINTMENT_NOT_FOUND", error.code());
        verify(queries).findScoped(argThat(scope -> scope.type() == ScopeType.PATIENT && Set.of(patient).equals(scope.profileIds())), eq(appointment));
    }

    @Test void doctorDetailRequiresCurrentAssignedDoctorProfile() {
        when(doctorAccess.isActiveDoctorAccount(account)).thenReturn(true);
        when(staff.findDoctorByAccount(eq(account), any(), any())).thenReturn(new StaffDirectoryQueryService.DoctorBookingProfile(doctor, false, false, false, false));
        when(queries.findScoped(any(), eq(appointment))).thenReturn(Optional.empty());
        assertEquals("APPOINTMENT_NOT_FOUND", assertThrows(AppointmentQueryException.class,
                () -> handler.detail(account, AccountRole.DOCTOR, appointment)).code());
    }

    @Test void lockedDoctorCannotQueryAppointments() {
        when(doctorAccess.isActiveDoctorAccount(account)).thenReturn(false);
        assertEquals("ACCESS_DENIED", assertThrows(AppointmentQueryException.class,
                () -> handler.list(account, AccountRole.DOCTOR, new AppointmentSearchFilter(null, null, null, null, null), 0, 20)).code());
        verifyNoInteractions(staff, queries);
    }

    @Test void unsupportedRoleIsDeniedBeforeQuery() {
        assertEquals("ACCESS_DENIED", assertThrows(AppointmentQueryException.class,
                () -> handler.list(account, AccountRole.ADMIN, new AppointmentSearchFilter(null, null, null, null, null), 0, 20)).code());
        verifyNoInteractions(queries);
    }
}
