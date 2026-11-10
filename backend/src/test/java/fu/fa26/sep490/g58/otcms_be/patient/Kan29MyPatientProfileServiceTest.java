package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.exception.MyPatientProfileException;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.CreateMyPatientProfileRequest;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.UpdatePatientProfileRequest;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.MyPatientProfileServiceImpl;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Kan29MyPatientProfileServiceTest {
    final PatientIdentityDirectory identities=mock(PatientIdentityDirectory.class);
    final PatientProfileDirectory profiles=mock(PatientProfileDirectory.class);
    final MyPatientProfileServiceImpl service=new MyPatientProfileServiceImpl(identities,profiles,mock(CoordinationGuardService.class));
    final UUID account=UUID.randomUUID(), profile=UUID.randomUUID();
    final AuthenticatedAccount actor=new AuthenticatedAccount(account,AccountRole.PATIENT,null);
    void own(UUID owner,long version) {
        when(identities.isActivePatient(account)).thenReturn(true);
        when(profiles.findPatientProfile(account)).thenReturn(Optional.of(profile));
        when(profiles.findProfile(profile)).thenReturn(Optional.of(new ProfileRecord(profile,owner,"Name","+84912345678","PATIENT",
                LocalDate.of(2000,1,2),"OTHER",null,null,null,version)));
    }
    UpdatePatientProfileRequest input(String name,Long version) {
        return new UpdatePatientProfileRequest(name,"0912345678","PATIENT",LocalDate.of(2000,1,2),"OTHER",null,null,null,version);
    }
    CreateMyPatientProfileRequest create(String name,String address) {
        return new CreateMyPatientProfileRequest(name,"0912345678",LocalDate.of(2000,1,2),"MALE",address,null,null);
    }
    @Test void firstProfileIsCreatedForTheSignedInAccountWithPatientOwnedPhone() {
        when(identities.isActivePatient(account)).thenReturn(true);
        when(profiles.findPatientProfile(account)).thenReturn(Optional.empty());
        when(profiles.create(any(),any())).thenAnswer(call->call.getArgument(0));
        var created=service.create(actor,create("Nguyen Van An","12 Nguyen Hue"));
        var saved=org.mockito.ArgumentCaptor.forClass(ProfileRecord.class);
        verify(profiles).create(saved.capture(),eq("PATIENT"));
        assertEquals(account,saved.getValue().accountId());
        assertEquals("Nguyen Van An",created.fullName());
        assertTrue(created.hasAccount());
    }
    @Test void createRequiresAddressAndReportsFieldCodes() {
        when(identities.isActivePatient(account)).thenReturn(true);
        var failure=assertThrows(MyPatientProfileException.class,()->service.create(actor,create("Nguyen Van An"," ")));
        assertEquals(MyPatientProfileException.Reason.INVALID_INPUT,failure.reason());
        assertEquals("address",failure.fields().getFirst().field());
        verify(profiles,never()).create(any(),any());
    }
    @Test void secondProfileForSameAccountIsRejected() {
        when(identities.isActivePatient(account)).thenReturn(true);
        when(profiles.findPatientProfile(account)).thenReturn(Optional.of(profile));
        assertEquals(MyPatientProfileException.Reason.ALREADY_EXISTS,assertThrows(MyPatientProfileException.class,()->service.create(actor,create("Nguyen Van An","12 Nguyen Hue"))).reason());
        verify(profiles,never()).create(any(),any());
    }
    @Test void counterProfileWithSamePhoneDobAndNameIsNotDuplicated() {
        when(identities.isActivePatient(account)).thenReturn(true);
        when(profiles.findPatientProfile(account)).thenReturn(Optional.empty());
        when(profiles.duplicateExists(any(),any(),any())).thenReturn(true);
        assertEquals(MyPatientProfileException.Reason.DUPLICATE,assertThrows(MyPatientProfileException.class,()->service.create(actor,create("Nguyen Van An","12 Nguyen Hue"))).reason());
        verify(profiles,never()).create(any(),any());
    }
    @Test void nonPatientCannotCreateProfile() {
        var staff=new AuthenticatedAccount(account,AccountRole.RECEPTIONIST,null);
        assertEquals(MyPatientProfileException.Reason.ACCESS_DENIED,assertThrows(MyPatientProfileException.class,()->service.create(staff,create("Nguyen Van An","12 Nguyen Hue"))).reason());
        verifyNoInteractions(profiles);
    }
    @Test void ownershipMismatchCannotReachUpdate() {
        own(UUID.randomUUID(),0);
        assertEquals(MyPatientProfileException.Reason.ACCESS_DENIED,assertThrows(MyPatientProfileException.class,()->service.update(actor,input("Valid Name",0L))).reason());
        verify(profiles,never()).update(any(),anyLong());
    }
    @Test void kan24ValidationIsTranslatedToScopedException() {
        own(account,0);
        var failure=assertThrows(MyPatientProfileException.class,()->service.update(actor,input("X",0L)));
        assertEquals(MyPatientProfileException.Reason.INVALID_INPUT,failure.reason());
        assertEquals("MSG-08",failure.fields().getFirst().code());
        verify(profiles,never()).update(any(),anyLong());
    }
    @Test void staleVersionCannotReachPersistence() {
        own(account,1);
        assertEquals(MyPatientProfileException.Reason.STALE_VERSION,assertThrows(MyPatientProfileException.class,()->service.update(actor,input("Valid Name",0L))).reason());
        verify(profiles,never()).update(any(),anyLong());
    }
    @Test void inactivePatientCannotResolveAnyProfile() {
        assertThrows(MyPatientProfileException.class,()->service.get(actor));
        verifyNoInteractions(profiles);
    }
}
