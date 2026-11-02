package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.*;
import fu.fa26.sep490.g58.otcms_be.patient.mapper.CareRelationshipMapper;
import fu.fa26.sep490.g58.otcms_be.patient.repository.CareRelationshipRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientProfileAccessServiceTest {
    final UUID actor = UUID.randomUUID(), own = UUID.randomUUID(), dependent = UUID.randomUUID();
    final PatientIdentityDirectory identities = mock(PatientIdentityDirectory.class);
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final CareRelationshipRepository links = mock(CareRelationshipRepository.class);
    final PatientProfileAccessServiceImpl service = new PatientProfileAccessServiceImpl(identities, profiles, links);

    @Test void activeSelfAndConfirmedOnlyDedupeAndBillingDelegates() {
        when(identities.isActivePatient(actor)).thenReturn(true);
        when(profiles.findPatientProfile(actor)).thenReturn(Optional.of(own));
        var confirmed = CareRelationshipMapper.toEntity(new CareRelationship(UUID.randomUUID(), actor, dependent,
                null, State.CONFIRMED, Instant.EPOCH, Instant.EPOCH, null, null));
        when(links.findAllByRequesterAccountIdAndState(actor, State.CONFIRMED)).thenReturn(List.of(confirmed, confirmed));
        assertEquals(Set.of(own, dependent), service.accessibleProfileIds(actor));
        assertTrue(service.canAccess(actor, own)); assertTrue(service.canAccess(actor, dependent));
        assertFalse(service.canAccess(actor, UUID.randomUUID())); assertFalse(service.canAccess(actor, null));
        verify(links, atLeastOnce()).findAllByRequesterAccountIdAndState(actor, State.CONFIRMED);
        assertEquals(Set.of(own, dependent), new HashSet<>(new AuthorizePatientBillingProfileServiceImpl(service).accessibleProfileIds(actor)));
    }
    @Test void nullInactiveNonpatientAndMissingSelfFailClosed() {
        assertTrue(service.accessibleProfileIds(null).isEmpty()); assertFalse(service.canAccess(actor, own));
        when(identities.isActivePatient(actor)).thenReturn(true);
        assertTrue(service.accessibleProfileIds(actor).isEmpty()); verifyNoInteractions(links);
    }
}
