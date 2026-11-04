package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.MedicalHistoryException;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.MedicalHistoryDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.repository.PatientMedicalHistoryRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MedicalHistoryServiceTest {
    final UUID actor = UUID.randomUUID(), target = UUID.randomUUID();
    final PatientIdentityDirectory patients = mock(PatientIdentityDirectory.class);
    final DoctorAccountAccessService doctors = mock(DoctorAccountAccessService.class);
    final DoctorCareRelationPort relations = mock(DoctorCareRelationPort.class);
    final PatientProfileAccessService access = mock(PatientProfileAccessService.class);
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final MyAccountQueryService accounts = mock(MyAccountQueryService.class);
    final PatientMedicalHistoryRepository histories = mock(PatientMedicalHistoryRepository.class);
    final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    final PatientMedicalHistoryService service = new PatientMedicalHistoryServiceImpl(patients, doctors,
            relations, access, profiles, accounts, histories, guard, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    AuthenticatedAccount principal(AccountRole role) { return new AuthenticatedAccount(actor, role, UUID.randomUUID()); }
    void error(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(MedicalHistoryException.class, action).getMessage());
    }
    @Test void patientAccessIsCheckedBeforeExistenceOrHistory() {
        when(patients.isActivePatient(actor)).thenReturn(true);
        error("PATIENT_PROFILE_NOT_FOUND", () -> service.get(principal(AccountRole.PATIENT), target));
        verify(access).canAccess(actor, target);
        verifyNoInteractions(profiles, histories, accounts, relations);
    }
    @Test void inactiveDoctorCannotBypassThroughPermissivePort() {
        when(relations.isResponsibleDoctor(actor, target)).thenReturn(true);
        error("ACCESS_DENIED", () -> service.get(principal(AccountRole.DOCTOR), target));
        verifyNoInteractions(relations, histories, profiles);
    }
    @Test void futureDoctorRelationDenialPrecedesProfileLookup() {
        when(doctors.isActiveDoctorAccount(actor)).thenReturn(true);
        error("ACCESS_DENIED", () -> service.get(principal(AccountRole.DOCTOR), target));
        verify(relations).isResponsibleDoctor(actor, target);
        verifyNoInteractions(profiles, histories, access);
    }
    @ParameterizedTest @EnumSource(value = AccountRole.class, names = {"PATIENT", "DOCTOR"}, mode = EnumSource.Mode.EXCLUDE)
    void otherRolesFailAtServiceBoundary(AccountRole role) {
        error("ACCESS_DENIED", () -> service.get(principal(role), target));
        verifyNoInteractions(histories, profiles, access, relations);
    }
    @Test void retiredTemporaryAdapterCannotAuthorizeEvenAnActiveDoctor() {
        var adapter = new TemporaryR3DoctorCareRelationAdapter(doctors);
        assertFalse(adapter.isResponsibleDoctor(null, target));
        assertFalse(adapter.isResponsibleDoctor(actor, target));
        when(doctors.isActiveDoctorAccount(actor)).thenReturn(true);
        assertFalse(adapter.isResponsibleDoctor(actor, target));
        assertFalse(adapter.isResponsibleDoctor(actor, null));
    }
    @Test void staleWriteDoesNotReadOrReturnHistory() {
        when(patients.isActivePatient(actor)).thenReturn(true);
        when(access.canAccess(actor, target)).thenReturn(true);
        when(profiles.findProfile(target)).thenReturn(Optional.of(new PatientProfileDirectory.ProfileRecord(
                target, actor, "Patient", null, null, LocalDate.of(2000, 1, 2), "OTHER")));
        error("STALE_VERSION", () -> service.update(principal(AccountRole.PATIENT), target,
                new UpdateMedicalHistoryRequest("", "", "", "", "", 1L)));
        var order = inOrder(guard, access, histories);
        order.verify(guard).acquire(); order.verify(access).canAccess(actor, target);
        order.verify(histories).updateIfVersion(target, "", "", "", "", "", Instant.EPOCH, actor, 1);
        verify(histories, never()).findById(any()); verify(histories, never()).save(any());
    }
    @Test void metadataUsesOnlyPublicDisplayNameAndDoesNotHideUnexpectedFailure() {
        when(patients.isActivePatient(actor)).thenReturn(true);
        when(access.canAccess(actor, target)).thenReturn(true);
        when(profiles.findProfile(target)).thenReturn(Optional.of(new PatientProfileDirectory.ProfileRecord(
                target, actor, "Patient", null, null, LocalDate.of(2000, 1, 2), "OTHER")));
        var row = mock(fu.fa26.sep490.g58.otcms_be.patient.model.entity.PatientMedicalHistoryJpaEntity.class);
        when(row.getUpdatedByAccountId()).thenReturn(actor); when(row.getUpdatedAt()).thenReturn(Instant.EPOCH);
        when(histories.findById(target)).thenReturn(Optional.of(row));
        when(accounts.find(actor)).thenReturn(Optional.of(new MyAccountQueryService.MyAccountView(actor, "PATIENT", "private@example.test", "Editor")));
        assertEquals("Editor", service.get(principal(AccountRole.PATIENT), target).updatedBy());
        when(accounts.find(actor)).thenReturn(Optional.empty());
        assertNull(service.get(principal(AccountRole.PATIENT), target).updatedBy());
        when(accounts.find(actor)).thenThrow(new IllegalStateException("Unavailable account lookup"));
        assertThrows(IllegalStateException.class, () -> service.get(principal(AccountRole.PATIENT), target));
    }
}
