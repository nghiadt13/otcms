package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalDoctorIdentityResolverTest {
    private final DoctorProfileIdentityQueryService doctors = mock(DoctorProfileIdentityQueryService.class);
    private final ClinicalDoctorIdentityResolver resolver = new ClinicalDoctorIdentityResolver(doctors);

    @Test void nullAccountAndAccountIdAreDeniedBeforeLookup() {
        denied(null);
        denied(new AuthenticatedAccount(null, AccountRole.DOCTOR, null));
        verifyNoInteractions(doctors);
    }

    @Test void everyWrongRoleIsDeniedBeforeLookup() {
        for (var role : AccountRole.values()) {
            if (role != AccountRole.DOCTOR) denied(new AuthenticatedAccount(UUID.randomUUID(), role, null));
        }
        verifyNoInteractions(doctors);
    }

    @Test void doctorWithoutProfileIsDenied() {
        UUID accountId = UUID.randomUUID();
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.empty());
        denied(new AuthenticatedAccount(accountId, AccountRole.DOCTOR, null));
    }

    @Test void resolvesProfileUsingAuthenticatedAccountId() {
        UUID accountId = UUID.randomUUID(), profileId = UUID.randomUUID();
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.of(profileId));
        assertEquals(profileId, resolver.requireDoctorProfile(new AuthenticatedAccount(accountId, AccountRole.DOCTOR, null)));
        verify(doctors).findDoctorProfileId(accountId);
    }

    private void denied(AuthenticatedAccount account) {
        assertEquals("MSG-40", assertThrows(ClinicalOperationException.class,
                () -> resolver.requireDoctorProfile(account)).code());
    }
}
