package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientProfileException;
import fu.fa26.sep490.g58.otcms_be.patient.model.ReceptionVerificationProfile;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.PatientProfileServiceImpl;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientProfileReceptionVerificationT046Test {
    private final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    private final ReceptionistAccountAccessService receptionists = mock(ReceptionistAccountAccessService.class);
    private final PatientProfileServiceImpl service = new PatientProfileServiceImpl(
            profiles, mock(CoordinationGuardService.class), receptionists);
    private final UUID actor = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();

    @Test
    void returnsOnlyTheApprovedReceptionProjectionForAnActiveReceptionist() {
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(true);
        when(profiles.findProfile(patientId)).thenReturn(Optional.of(new PatientProfileDirectory.ProfileRecord(
                patientId, UUID.randomUUID(), "Patient Example", "+84912345678", "PATIENT",
                LocalDate.of(1990, 1, 2), "OTHER")));

        ReceptionVerificationProfile result = service.getReceptionVerificationProfile(actor, patientId);

        assertEquals(new ReceptionVerificationProfile(patientId, "Patient Example", LocalDate.of(1990, 1, 2)), result);
        verify(profiles).findProfile(patientId);
        verifyNoMoreInteractions(profiles);
    }

    @Test
    void deniesInactiveOrNonReceptionistBeforeLookingUpProfile() {
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(false);

        assertEquals("FORBIDDEN", assertThrows(PatientProfileException.class,
                () -> service.getReceptionVerificationProfile(actor, patientId)).getMessage());
        verifyNoInteractions(profiles);
    }

    @Test
    void reportsUnknownProfileWithoutReturningOtherPatientData() {
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(true);
        when(profiles.findProfile(patientId)).thenReturn(Optional.empty());

        assertEquals("PATIENT_PROFILE_NOT_FOUND", assertThrows(PatientProfileException.class,
                () -> service.getReceptionVerificationProfile(actor, patientId)).getMessage());
    }
}
