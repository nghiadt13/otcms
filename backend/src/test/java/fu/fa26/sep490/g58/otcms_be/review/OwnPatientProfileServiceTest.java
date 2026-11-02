package fu.fa26.sep490.g58.otcms_be.review;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.review.exception.ReviewOperationException;
import fu.fa26.sep490.g58.otcms_be.review.service.impl.OwnPatientProfileServiceImpl;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnPatientProfileServiceTest {
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final AuthorizePatientProfileService authorization = mock(AuthorizePatientProfileService.class);
    final OwnPatientProfileServiceImpl service = new OwnPatientProfileServiceImpl(profiles, authorization);
    @Test void resolvesOnlyOwnProfileAndChecksExistingOwnershipPolicy() {
        UUID account = UUID.randomUUID(), profile = UUID.randomUUID();
        when(profiles.findPatientProfile(account)).thenReturn(Optional.of(profile));
        var record = new PatientProfileDirectory.ProfileRecord(profile, account, "Patient", null, null, null, null);
        when(profiles.findProfile(profile)).thenReturn(Optional.of(record));
        assertSame(record, service.requireOwnProfile(account));
        verify(authorization).authorize(account, profile);
        when(authorization.authorize(account, profile)).thenThrow(new PatientAuthorizationDeniedException());
        assertEquals(403, assertThrows(ReviewOperationException.class, () -> service.requireOwnProfile(account)).status());
    }
    @Test void missingProfileDeniedWithoutAcceptingClientProfileId() {
        UUID account = UUID.randomUUID();
        when(profiles.findPatientProfile(account)).thenReturn(Optional.empty());
        assertEquals("MSG-56", assertThrows(ReviewOperationException.class, () -> service.requireOwnProfile(account)).code());
        verifyNoInteractions(authorization);
    }
}
