package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.DoctorActiveVisitReadServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService.ClinicalIdentity;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitDoctorActiveCareReadService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitDoctorActiveCareReadService.DoctorActiveVisit;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DoctorActiveVisitReadServiceImplTest {
    private final DoctorProfileIdentityQueryService doctorProfiles = mock(DoctorProfileIdentityQueryService.class);
    private final VisitDoctorActiveCareReadService visits = mock(VisitDoctorActiveCareReadService.class);
    private final PatientClinicalIdentityQueryService patients = mock(PatientClinicalIdentityQueryService.class);
    private final DoctorActiveVisitReadServiceImpl service = new DoctorActiveVisitReadServiceImpl(
            new ClinicalDoctorIdentityResolver(doctorProfiles), visits, patients);
    private final UUID accountId = UUID.randomUUID(), doctorId = UUID.randomUUID(), patientId = UUID.randomUUID();
    private final AuthenticatedAccount account = new AuthenticatedAccount(accountId, AccountRole.DOCTOR, null);

    @Test void emptyActiveCareDoesNotLookUpPatientData() {
        identity(); when(visits.findActiveVisits(doctorId)).thenReturn(List.of());
        assertTrue(service.findActiveVisits(account).items().isEmpty());
        verifyNoInteractions(patients); verify(doctorProfiles).findDoctorProfileId(accountId);
    }

    @Test void composesScopedVisitsAndDistinctMinimalPatientIdentityInOriginalOrder() {
        identity(); var a = visit(); var b = visit();
        when(visits.findActiveVisits(doctorId)).thenReturn(List.of(a, b));
        when(patients.findClinicalIdentities(List.of(patientId))).thenReturn(Map.of(patientId, patient()));
        var result = service.findActiveVisits(account);
        assertEquals(List.of(a.visitId(), b.visitId()), result.items().stream().map(item -> item.visitId()).toList());
        assertEquals("Patient A", result.items().getFirst().patient().fullName());
        assertEquals(LocalDate.of(2000, 1, 1), result.items().getFirst().patient().dateOfBirth());
        verify(visits).findActiveVisits(doctorId); verify(patients).findClinicalIdentities(List.of(patientId));
        assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
    }

    @Test void missingPatientIdentityFailsWholeResponseInsteadOfOmittingItem() {
        identity(); when(visits.findActiveVisits(doctorId)).thenReturn(List.of(visit()));
        when(patients.findClinicalIdentities(anyCollection())).thenReturn(Map.of());
        assertEquals("DEPENDENCY_UNAVAILABLE", assertThrows(ClinicalOperationException.class,
                () -> service.findActiveVisits(account)).code());
    }

    @Test void mismatchedPatientIdentityCannotExposeAnotherPatient() {
        identity(); when(visits.findActiveVisits(doctorId)).thenReturn(List.of(visit()));
        when(patients.findClinicalIdentities(anyCollection())).thenReturn(Map.of(patientId,
                new ClinicalIdentity(UUID.randomUUID(), "Different patient", LocalDate.of(2000, 1, 1), "OTHER")));
        assertEquals("DEPENDENCY_UNAVAILABLE", assertThrows(ClinicalOperationException.class,
                () -> service.findActiveVisits(account)).code());
    }

    @Test void missingProfileOrWrongPrincipalDoesNotQueryVisitOrPatient() {
        when(doctorProfiles.findDoctorProfileId(accountId)).thenReturn(Optional.empty());
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class, () -> service.findActiveVisits(account)).code());
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class, () -> service.findActiveVisits(null)).code());
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class, () -> service.findActiveVisits(
                new AuthenticatedAccount(accountId, AccountRole.RECEPTIONIST, null))).code());
        verifyNoInteractions(visits, patients);
    }

    @Test void downstreamFailureDoesNotTurnIntoEmptySuccessfulList() {
        identity(); when(visits.findActiveVisits(doctorId)).thenThrow(new IllegalStateException("Care read unavailable"));
        assertThrows(IllegalStateException.class, () -> service.findActiveVisits(account));
        verifyNoInteractions(patients);
    }

    private void identity() { when(doctorProfiles.findDoctorProfileId(accountId)).thenReturn(Optional.of(doctorId)); }
    private ClinicalIdentity patient() { return new ClinicalIdentity(patientId, "Patient A", LocalDate.of(2000, 1, 1), "MALE"); }
    private DoctorActiveVisit visit() {
        return new DoctorActiveVisit(UUID.randomUUID(), patientId, "WALK_IN", null, UUID.randomUUID(), "EXAMINATION",
                Instant.parse("2026-11-04T14:00:00Z"), Instant.parse("2026-11-04T14:30:00Z"));
    }
}
