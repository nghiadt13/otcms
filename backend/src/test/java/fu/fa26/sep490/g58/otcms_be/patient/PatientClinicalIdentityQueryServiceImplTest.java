package fu.fa26.sep490.g58.otcms_be.patient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService.ClinicalIdentity;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.PatientClinicalIdentityQueryServiceImpl;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class PatientClinicalIdentityQueryServiceImplTest {
    private final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    private final PatientClinicalIdentityQueryServiceImpl service = new PatientClinicalIdentityQueryServiceImpl(profiles);

    @Test void emptyAndNullCollectionsReturnEmptyWithoutLookup() {
        assertTrue(service.findClinicalIdentities(List.of()).isEmpty());
        assertTrue(service.findClinicalIdentities(null).isEmpty());
        verifyNoInteractions(profiles);
    }

    @Test void knownProfileReturnsOnlyFourClinicalIdentityFieldsWithoutMutation() {
        UUID id = UUID.randomUUID();
        when(profiles.findProfiles(Set.of(id))).thenReturn(List.of(profile(id, "Patient A")));
        var identities = service.findClinicalIdentities(List.of(id));
        assertEquals(new ClinicalIdentity(id, "Patient A", LocalDate.of(2000, 1, 1), "MALE"), identities.get(id));
        var json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()
                .readTree(tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(identities.get(id)));
        assertEquals(Set.of("patientProfileId", "fullName", "dateOfBirth", "gender"), json.propertyNames());
        verify(profiles).findProfiles(Set.of(id));
        verifyNoMoreInteractions(profiles);
        assertThrows(UnsupportedOperationException.class, () -> identities.clear());
    }

    @Test void multipleProfilesUseOneBatchWithDistinctIds() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        when(profiles.findProfiles(Set.of(a, b))).thenReturn(List.of(profile(a, "Patient A"), profile(b, "Patient B")));
        var result = service.findClinicalIdentities(List.of(a, b, a));
        assertEquals(2, result.size());
        assertEquals("Patient A", result.get(a).fullName());
        assertEquals("Patient B", result.get(b).fullName());
        verify(profiles).findProfiles(Set.of(a, b)); verifyNoMoreInteractions(profiles);
    }

    @Test void missingProfileIsOmittedSoCallerCanFailClosed() {
        UUID id = UUID.randomUUID();
        when(profiles.findProfiles(Set.of(id))).thenReturn(List.of());
        assertTrue(service.findClinicalIdentities(List.of(id)).isEmpty());
    }

    @Test void nullIdsAreRejectedBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class, () -> service.findClinicalIdentities(Arrays.asList(UUID.randomUUID(), null)));
        verifyNoInteractions(profiles);
    }

    @Test void infrastructureFailureDoesNotBecomeMissingIdentity() {
        UUID id = UUID.randomUUID();
        var failure = new IllegalStateException("Profile lookup unavailable");
        when(profiles.findProfiles(Set.of(id))).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.findClinicalIdentities(List.of(id))));
    }

    private PatientProfileDirectory.ProfileRecord profile(UUID id, String name) {
        return new PatientProfileDirectory.ProfileRecord(id, UUID.randomUUID(), name, "private-phone", "GUARDIAN",
                LocalDate.of(2000, 1, 1), "MALE", "private-address", "private-national-id", "private-document-type", 3);
    }
}
