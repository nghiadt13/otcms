package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.exception.MyPatientProfileException;
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
    final MyPatientProfileServiceImpl service=new MyPatientProfileServiceImpl(identities,profiles);
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
