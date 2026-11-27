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
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Branch-focused companion of MedicalHistoryServiceTest: validation, null guards, write paths and display-name rules. */
class PatientMedicalHistoryServiceImplTest {
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

    void patientCanReachProfile() {
        when(patients.isActivePatient(actor)).thenReturn(true);
        when(access.canAccess(actor, target)).thenReturn(true);
        when(profiles.findProfile(target)).thenReturn(Optional.of(new PatientProfileDirectory.ProfileRecord(
                target, actor, "Patient", null, null, LocalDate.of(2000, 1, 2), "OTHER")));
    }

    // Answer-based mock: no when(...) inside another stubbing call (avoids UnfinishedStubbing).
    fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity savedRow(Instant updatedAt, UUID updatedBy) {
        return mock(fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity.class,
                invocation -> switch (invocation.getMethod().getName()) {
                    case "getMedicalHistoryUpdatedBy" -> updatedBy;
                    case "getMedicalHistoryUpdatedAt" -> updatedAt;
                    case "getConditions" -> "c";
                    case "getAllergies" -> "a";
                    case "getProcedures" -> "p";
                    case "getInjuries" -> "i";
                    case "getOrthopedicHistory" -> "o";
                    case "getMedicalHistoryVersion" -> 3L;
                    default -> null;
                });
    }

    @Test void getRejectsNullActor() {
        error("ACCESS_DENIED", () -> service.get(null, target));
        verifyNoInteractions(histories, profiles);
    }

    @Test void getRejectsActorWithoutAccountId() {
        error("ACCESS_DENIED", () -> service.get(new AuthenticatedAccount(null, AccountRole.PATIENT, UUID.randomUUID()), target));
        verifyNoInteractions(histories, profiles);
    }

    @Test void getRejectsActorWithoutRole() {
        error("ACCESS_DENIED", () -> service.get(new AuthenticatedAccount(actor, null, UUID.randomUUID()), target));
        verifyNoInteractions(histories, profiles);
    }

    @Test void getRejectsNullProfileIdAsNotFound() {
        when(patients.isActivePatient(actor)).thenReturn(true);
        error("PATIENT_PROFILE_NOT_FOUND", () -> service.get(principal(AccountRole.PATIENT), null));
        verifyNoInteractions(histories, profiles);
    }

    @Test void getReportsNotFoundWhenProfileMissingAfterAuthorization() {
        when(patients.isActivePatient(actor)).thenReturn(true);
        when(access.canAccess(actor, target)).thenReturn(true);
        when(profiles.findProfile(target)).thenReturn(Optional.empty());
        error("PATIENT_PROFILE_NOT_FOUND", () -> service.get(principal(AccountRole.PATIENT), target));
        verifyNoInteractions(histories);
    }

    @Test void getAllowsResponsibleDoctorAndReturnsEmptyWhenNoHistoryWasWritten() {
        when(doctors.isActiveDoctorAccount(actor)).thenReturn(true);
        when(relations.isResponsibleDoctor(actor, target)).thenReturn(true);
        when(profiles.findProfile(target)).thenReturn(Optional.of(new PatientProfileDirectory.ProfileRecord(
                target, UUID.randomUUID(), "Patient", null, null, LocalDate.of(2000, 1, 2), "OTHER")));
        when(histories.findById(target)).thenReturn(Optional.empty());

        assertEquals(MedicalHistoryResponse.empty(), service.get(principal(AccountRole.DOCTOR), target));
    }

    @Test void getReturnsEmptyWhenStoredHistoryHasNoUpdateTimestamp() {
        patientCanReachProfile();
        when(histories.findById(target)).thenReturn(Optional.of(savedRow(null, actor)));

        assertEquals(MedicalHistoryResponse.empty(), service.get(principal(AccountRole.PATIENT), target));
        verifyNoInteractions(accounts);
    }

    @Test void getHandlesNullMedicalHistoryUpdatedByWithoutQueryingAccounts() {
        patientCanReachProfile();
        when(histories.findById(target)).thenReturn(Optional.of(savedRow(Instant.EPOCH, null)));

        MedicalHistoryResponse response = service.get(principal(AccountRole.PATIENT), target);

        assertEquals("c", response.conditions());
        assertNull(response.updatedBy());
        assertEquals(3L, response.version());
        verifyNoInteractions(accounts);
    }

    @Test void blankOrMissingDisplayNameIsReportedAsNullUpdatedBy() {
        patientCanReachProfile();
        when(histories.findById(target)).thenReturn(Optional.of(savedRow(Instant.EPOCH, actor)));
        when(accounts.find(actor)).thenReturn(Optional.of(new MyAccountQueryService.MyAccountView(actor, "PATIENT", "x@example.test", "   ")));

        assertNull(service.get(principal(AccountRole.PATIENT), target).updatedBy());

        when(accounts.find(actor)).thenReturn(Optional.of(new MyAccountQueryService.MyAccountView(actor, "PATIENT", "x@example.test", null)));

        assertNull(service.get(principal(AccountRole.PATIENT), target).updatedBy());
    }

    @Test void updateRejectsNullHistoryRequest() {
        patientCanReachProfile();
        error("VALIDATION_FAILED", () -> service.update(principal(AccountRole.PATIENT), target, null));
        verifyNoInteractions(histories);
    }

    @Test void updateRejectsNullActorBeforeAcquiringGuard() {
        error("ACCESS_DENIED", () -> service.update(null, target,
                new UpdateMedicalHistoryRequest("", "", "", "", "", 0L)));
        verifyNoInteractions(guard, histories);
    }

    @Test void updateRejectsNegativeVersion() {
        patientCanReachProfile();
        error("VALIDATION_FAILED", () -> service.update(principal(AccountRole.PATIENT), target,
                new UpdateMedicalHistoryRequest("", "", "", "", "", -1L)));
        verifyNoInteractions(histories);
    }

    @Test void updateRejectsMissingVersion() {
        patientCanReachProfile();
        error("VALIDATION_FAILED", () -> service.update(principal(AccountRole.PATIENT), target,
                new UpdateMedicalHistoryRequest("", "", "", "", "", null)));
        verifyNoInteractions(histories);
    }

    @Test void updateRejectsNullTextField() {
        patientCanReachProfile();
        error("VALIDATION_FAILED", () -> service.update(principal(AccountRole.PATIENT), target,
                new UpdateMedicalHistoryRequest("ok", null, "ok", "ok", "ok", 0L)));
        verifyNoInteractions(histories);
    }

    @Test void updateRejectsTextLongerThan2000Characters() {
        patientCanReachProfile();
        error("VALIDATION_FAILED", () -> service.update(principal(AccountRole.PATIENT), target,
                new UpdateMedicalHistoryRequest("x".repeat(2001), "", "", "", "", 0L)));
        verifyNoInteractions(histories);
    }

    @Test void updateAcceptsExactly2000CharactersAndTrimsBeforeInsertingInitialHistory() {
        patientCanReachProfile();
        when(histories.insertInitial(eq(target), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), eq(actor))).thenReturn(1);
        when(histories.findById(target)).thenReturn(Optional.empty());

        MedicalHistoryResponse response = service.update(principal(AccountRole.PATIENT), target,
                new UpdateMedicalHistoryRequest("  " + "x".repeat(2000) + "  ", "a", "p", "i", "o", 0L));

        assertEquals(MedicalHistoryResponse.empty(), response);
        verify(histories).insertInitial(target, "x".repeat(2000), "a", "p", "i", "o", Instant.EPOCH, actor);
        verify(histories, never()).updateIfVersion(any(), any(), any(), any(), any(), any(), any(), any(), anyLong());
    }

    @Test void updateWithPositiveVersionUsesVersionedUpdateAndReturnsStoredHistory() {
        patientCanReachProfile();
        when(histories.updateIfVersion(eq(target), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), eq(actor), eq(3L))).thenReturn(1);
        when(histories.findById(target)).thenReturn(Optional.of(savedRow(Instant.EPOCH, actor)));
        when(accounts.find(actor)).thenReturn(Optional.of(new MyAccountQueryService.MyAccountView(actor, "PATIENT", "x@example.test", "Editor")));

        MedicalHistoryResponse response = service.update(principal(AccountRole.PATIENT), target,
                new UpdateMedicalHistoryRequest("c", "a", "p", "i", "o", 3L));

        assertEquals("c", response.conditions());
        assertEquals("Editor", response.updatedBy());
        assertEquals(3L, response.version());
        verify(histories, never()).insertInitial(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void updateReportsStaleVersionWhenNoRowWasWritten() {
        patientCanReachProfile();
        when(histories.updateIfVersion(eq(target), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), eq(actor), eq(2L))).thenReturn(0);

        error("STALE_VERSION", () -> service.update(principal(AccountRole.PATIENT), target,
                new UpdateMedicalHistoryRequest("c", "a", "p", "i", "o", 2L)));
        verify(histories, never()).findById(any());
    }
}
