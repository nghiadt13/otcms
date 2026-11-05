package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitReceptionReadRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitReceptionReadServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class VisitProgressAuthorizationTest {
    final UUID account = UUID.randomUUID(), doctorProfile = UUID.randomUUID(), patient = UUID.randomUUID(), visit = UUID.randomUUID();
    final VisitReceptionReadRepository reads = mock(VisitReceptionReadRepository.class);
    final ReceptionistAccountAccessService receptionists = mock(ReceptionistAccountAccessService.class);
    final AuthorizePatientProfileService patients = mock(AuthorizePatientProfileService.class);
    final DoctorAccountAccessService doctors = mock(DoctorAccountAccessService.class);
    final DoctorProfileIdentityQueryService profiles = mock(DoctorProfileIdentityQueryService.class);
    final VisitClinicalCareContextService care = mock(VisitClinicalCareContextService.class);
    final AppointmentBookingProperties booking = mock(AppointmentBookingProperties.class);
    final VisitReceptionReadServiceImpl service = new VisitReceptionReadServiceImpl(reads, receptionists, patients, booking, doctors, profiles, care);
    AuthenticatedAccount actor(AccountRole role) { return new AuthenticatedAccount(account, role, null); }
    Object[] row(UUID profile) {
        return new Object[]{visit, profile, "WALK_IN", "OPEN", Instant.parse("2026-11-05T00:00:00Z"), "EXAMINATION", null, null, "ROOM-1"};
    }
    void denial(Runnable operation) { assertEquals("ACCESS_DENIED", assertThrows(VisitOperationException.class, operation::run).code()); }
    void doctorCare(UUID assigned, boolean allowed) {
        when(doctors.isActiveDoctorAccount(account)).thenReturn(true);
        when(profiles.findDoctorProfileId(account)).thenReturn(Optional.of(doctorProfile));
        when(care.findClinicalCareContext(visit)).thenReturn(Optional.of(new VisitClinicalCareContextService.ClinicalCareContext(
                visit, patient, UUID.randomUUID(), assigned, allowed, allowed)));
    }
    @Test void currentDoctorUsesProfileIdentityAndCanonicalVisit() {
        doctorCare(doctorProfile, true);
        when(reads.findProgress(visit)).thenReturn(row(patient));
        var result = service.getProgress(actor(AccountRole.DOCTOR), visit);
        assertEquals(visit, result.visitId()); assertEquals("EXAMINATION", result.currentStage());
        assertEquals("OPEN", result.status()); assertNull(result.queuePosition());
        verify(profiles).findDoctorProfileId(account);
        verify(reads).findProgress(visit);
        verifyNoMoreInteractions(reads);
    }
    @Test void wrongDoctorAndClosedOrNonClinicalVisitDeniedBeforeProjection() {
        doctorCare(UUID.randomUUID(), true);
        denial(() -> service.getProgress(actor(AccountRole.DOCTOR), visit));
        doctorCare(doctorProfile, false);
        denial(() -> service.getProgress(actor(AccountRole.DOCTOR), visit));
        verifyNoInteractions(reads);
    }
    @Test void inactiveOrProfilelessDoctorDenied() {
        denial(() -> service.getProgress(actor(AccountRole.DOCTOR), visit));
        when(doctors.isActiveDoctorAccount(account)).thenReturn(true);
        denial(() -> service.getProgress(actor(AccountRole.DOCTOR), visit));
        verifyNoInteractions(reads, care);
    }
    @Test void activeReceptionistCanFollowOperationalVisitWhileInactiveCannot() {
        denial(() -> service.getProgress(actor(AccountRole.RECEPTIONIST), visit));
        when(receptionists.isActiveReceptionistAccount(account)).thenReturn(true);
        when(reads.findProgress(visit)).thenReturn(row(patient));
        assertEquals("ROOM-1", service.getProgress(actor(AccountRole.RECEPTIONIST), visit).currentRoom());
        verifyNoInteractions(doctors, profiles, patients);
    }
    @Test void mismatchedPatientInReadProjectionFailsClosed() {
        doctorCare(doctorProfile, true);
        when(reads.findProgress(visit)).thenReturn(row(UUID.randomUUID()));
        assertEquals("RESOURCE_NOT_FOUND", assertThrows(VisitOperationException.class,
                () -> service.getProgress(actor(AccountRole.DOCTOR), visit)).code());
    }
    @Test void patientStillUsesOwnOrConfirmedDependentOwnerAuthorization() {
        when(reads.findProgress(visit)).thenReturn(row(patient));
        assertEquals(visit, service.getProgress(actor(AccountRole.PATIENT), visit).visitId());
        verify(patients).authorize(account, patient);
        doThrow(new PatientAuthorizationDeniedException()).when(patients).authorize(account, patient);
        denial(() -> service.getProgress(actor(AccountRole.PATIENT), visit));
        verifyNoInteractions(doctors, profiles, care, receptionists);
    }
    @Test void anonymousAdminOwnerAndTechnicianDeniedWithoutInventedRoomScope() {
        denial(() -> service.getProgress(null, visit));
        for (var role : List.of(AccountRole.ADMIN, AccountRole.OWNER, AccountRole.IMAGING_TECHNICIAN, AccountRole.LAB_TECHNICIAN)) {
            denial(() -> service.getProgress(actor(role), visit));
        }
        verifyNoInteractions(reads, doctors, profiles, care);
    }
    @Test void existingReceptionPositionComesFromOwnerReadRatherThanSyntheticQueue() {
        when(receptionists.isActiveReceptionistAccount(account)).thenReturn(true);
        var row = row(patient);
        UUID entry = UUID.randomUUID(); row[6] = entry; row[7] = "WAITING";
        when(reads.findProgress(visit)).thenReturn(row);
        when(booking.clinicTimeZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(reads.findPosition(eq(entry), any(), any())).thenReturn(3);
        assertEquals(3, service.getProgress(actor(AccountRole.RECEPTIONIST), visit).queuePosition());
    }
}
