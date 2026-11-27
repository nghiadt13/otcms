package fu.fa26.sep490.g58.otcms_be.patient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.MyPatientProfileException;
import fu.fa26.sep490.g58.otcms_be.patient.exception.MyPatientProfileException.Reason;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.CreateMyPatientProfileRequest;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.UpdatePatientProfileRequest;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.MyPatientProfileServiceImpl;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class MyPatientProfileServiceImplTest {
    static final LocalDate BIRTH = LocalDate.of(1990, 5, 17);
    final UUID account = UUID.randomUUID();
    final UUID profileId = UUID.randomUUID();
    final PatientIdentityDirectory identities = mock(PatientIdentityDirectory.class);
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    final MyPatientProfileServiceImpl service = new MyPatientProfileServiceImpl(identities, profiles, guard);

    static Reason reasonOf(Executable action) {
        return assertThrows(MyPatientProfileException.class, action).reason();
    }

    AuthenticatedAccount patient() {
        return new AuthenticatedAccount(account, AccountRole.PATIENT, null);
    }

    CreateMyPatientProfileRequest createRequest() {
        return new CreateMyPatientProfileRequest("Nguyen An", "0901234567", BIRTH, "MALE", "Ha Noi", "012345678901",
                "CCCD");
    }

    UpdatePatientProfileRequest updateRequest(Long version) {
        return new UpdatePatientProfileRequest("Nguyen An", "0901234567", "PATIENT", BIRTH, "MALE", "Ha Noi",
                "012345678901", "CCCD", version);
    }

    ProfileRecord ownRecord(long version) {
        return new ProfileRecord(profileId, account, "Nguyen An", "+84901234567", "PATIENT", BIRTH, "MALE", "Ha Noi",
                "012345678901", "CCCD", version);
    }

    /** The actor is an active patient whose own profile is stored and linked to the account. */
    void linkedProfile() {
        when(identities.isActivePatient(account)).thenReturn(true);
        when(profiles.findPatientProfile(account)).thenReturn(Optional.of(profileId));
        when(profiles.findProfile(profileId)).thenReturn(Optional.of(ownRecord(3)));
    }

    @Test
    void createRejectsNonPatientActorAndInactivePatient() {
        var doctor = new AuthenticatedAccount(account, AccountRole.DOCTOR, null);
        var request = createRequest();

        assertEquals(Reason.ACCESS_DENIED, reasonOf(() -> service.create(doctor, request)));
        assertEquals(Reason.ACCESS_DENIED, reasonOf(() -> service.create(patient(), request)));
        verifyNoInteractions(guard, profiles);
    }

    @Test
    void createGetAndUpdateRejectActorWithNullAccountId() {
        var missingId = new AuthenticatedAccount(null, AccountRole.PATIENT, null);
        var createReq = createRequest();
        var updateReq = updateRequest(0L);

        assertEquals(Reason.ACCESS_DENIED, reasonOf(() -> service.create(missingId, createReq)));
        assertEquals(Reason.ACCESS_DENIED, reasonOf(() -> service.get(missingId)));
        assertEquals(Reason.ACCESS_DENIED, reasonOf(() -> service.update(missingId, updateReq)));
        verifyNoInteractions(identities, guard, profiles);
    }

    @Test
    void createRejectsMissingRequestBody() {
        when(identities.isActivePatient(account)).thenReturn(true);

        assertEquals(Reason.INVALID_INPUT, reasonOf(() -> service.create(patient(), null)));
        verifyNoInteractions(guard, profiles);
    }

    @Test
    void createFlagsProfileWithoutAccountLinkAsNotHavingAccount() {
        when(identities.isActivePatient(account)).thenReturn(true);
        when(profiles.findPatientProfile(account)).thenReturn(Optional.empty());
        when(profiles.create(any(), any())).thenReturn(new ProfileRecord(profileId, null, "Nguyen An", "+84901234567",
                "PATIENT", BIRTH, "MALE", "Ha Noi", "012345678901", "CCCD", 0L));

        var created = service.create(patient(), createRequest());

        assertFalse(created.hasAccount());
        verify(guard).acquire();
    }

    @Test
    void updateRejectsMissingRequestOrMissingOrNegativeVersion() {
        linkedProfile();

        assertEquals(Reason.INVALID_INPUT, reasonOf(() -> service.update(patient(), null)));
        var noVersion = updateRequest(null);
        assertEquals(Reason.INVALID_INPUT, reasonOf(() -> service.update(patient(), noVersion)));
        var negativeVersion = updateRequest(-1L);
        assertEquals(Reason.INVALID_INPUT, reasonOf(() -> service.update(patient(), negativeVersion)));
        verify(profiles, never()).update(any(), anyLong());
    }

    @Test
    void getRejectsNullActorAndNonPatientRole() {
        assertEquals(Reason.ACCESS_DENIED, reasonOf(() -> service.get(null)));
        var staff = new AuthenticatedAccount(account, AccountRole.RECEPTIONIST, null);
        assertEquals(Reason.ACCESS_DENIED, reasonOf(() -> service.get(staff)));
        verifyNoInteractions(profiles);
    }

    @Test
    void getReturnsOwnProfileMarkedAsLinkedToAccount() {
        linkedProfile();

        var own = service.get(patient());

        assertEquals(profileId, own.id());
        assertTrue(own.hasAccount());
        assertEquals(3L, own.version());
    }

    @Test
    void createRejectsDuplicateClinicProfileWithDuplicateReason() {
        when(identities.isActivePatient(account)).thenReturn(true);
        when(profiles.findPatientProfile(account)).thenReturn(Optional.empty());
        when(profiles.duplicateExists("+84901234567", BIRTH, "Nguyen An")).thenReturn(true);

        assertEquals(Reason.DUPLICATE, reasonOf(() -> service.create(patient(), createRequest())));
        verify(guard).acquire();
        verify(profiles, never()).create(any(), any());
    }

    @Test
    void updateValidProfileReturnsUpdatedVersion() {
        linkedProfile();
        ProfileRecord updated = ownRecord(4L);
        when(profiles.update(any(), eq(3L))).thenReturn(updated);

        var result = service.update(patient(), updateRequest(3L));

        assertEquals(4L, result.version());
        assertEquals(profileId, result.id());
    }

    @Test
    void updateRejectsStaleVersionAtMinusOneBoundary() {
        linkedProfile();

        assertEquals(Reason.STALE_VERSION, reasonOf(() -> service.update(patient(), updateRequest(2L))));
        verify(profiles, never()).update(any(), anyLong());
    }
}
