package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService.AuthorizationScope;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.AuthorizePatientProfileServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientAuthorizationT008Test {
    private final UUID actor = UUID.randomUUID();
    private final UUID own = UUID.randomUUID();
    private final UUID otherProfile = UUID.randomUUID();
    private final PatientIdentityDirectory identities = mock(PatientIdentityDirectory.class);
    private final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    private final AuthorizePatientProfileServiceImpl handler = new AuthorizePatientProfileServiceImpl(identities, profiles);

    private void linkedActor() {
        when(identities.isActivePatient(actor)).thenReturn(true);
        when(profiles.findPatientProfile(actor)).thenReturn(Optional.of(own));
    }

    @Test
    void authorizesOnlyCurrentLinkedSelfProfile() {
        linkedActor();

        var authorized = handler.authorize(actor, own);

        assertEquals(own, authorized.patientProfileId());
        assertEquals(AuthorizationScope.SELF, authorized.scope());
        verify(identities).isActivePatient(actor);
        verify(profiles).findPatientProfile(actor);
        verifyNoMoreInteractions(identities);
        verifyNoMoreInteractions(profiles);
    }

    @Test
    void deniesOtherPatientEvenWhenConfirmedCareRelationshipExists() {
        linkedActor();
        assertThrows(PatientAuthorizationDeniedException.class, () -> handler.authorize(actor, otherProfile));
    }

    @Test
    void missingOrNullProfileGetsSameScopedDenial() {
        linkedActor();

        assertThrows(PatientAuthorizationDeniedException.class, () -> handler.authorize(actor, null));
        assertThrows(PatientAuthorizationDeniedException.class, () -> handler.authorize(actor, otherProfile));
    }

    @Test
    void unlinkedDependentAndUnknownProfileCannotBeAuthorized() {
        linkedActor();
        assertThrows(PatientAuthorizationDeniedException.class, () -> handler.authorize(actor, otherProfile));
    }

    @Test
    void inactiveNonPatientOrUnlinkedRequesterFailsClosed() {
        assertThrows(PatientAuthorizationDeniedException.class, () -> handler.authorize(actor, own));

        when(identities.isActivePatient(actor)).thenReturn(true);
        assertThrows(PatientAuthorizationDeniedException.class, () -> handler.authorize(actor, own));
    }
}
