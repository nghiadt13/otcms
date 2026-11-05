package fu.fa26.sep490.g58.otcms_be.patient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.MedicalHistoryException;
import fu.fa26.sep490.g58.otcms_be.patient.integration.visit.VisitDoctorCareRelationAdapter;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.MedicalHistoryDtos.UpdateMedicalHistoryRequest;
import fu.fa26.sep490.g58.otcms_be.patient.repository.PatientMedicalHistoryRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.PatientMedicalHistoryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitDoctorCareRelationQueryServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class DoctorCurrentCareAuthorizationTest {
    final UUID accountId = UUID.randomUUID(), doctorId = UUID.randomUUID(), patientId = UUID.randomUUID();
    final DoctorAccountAccessService accounts = mock(DoctorAccountAccessService.class);
    final DoctorProfileIdentityQueryService identities = mock(DoctorProfileIdentityQueryService.class);
    final VisitCareAssignmentRepository assignments = mock(VisitCareAssignmentRepository.class);
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final PatientMedicalHistoryRepository histories = mock(PatientMedicalHistoryRepository.class);
    final VisitDoctorCareRelationAdapter relation = new VisitDoctorCareRelationAdapter(accounts, identities,
            new VisitDoctorCareRelationQueryServiceImpl(assignments));
    final PatientMedicalHistoryServiceImpl service = new PatientMedicalHistoryServiceImpl(mock(PatientIdentityDirectory.class),
            accounts, relation, mock(PatientProfileAccessService.class), profiles, mock(MyAccountQueryService.class), histories,
            mock(CoordinationGuardService.class), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    AuthenticatedAccount actor() { return new AuthenticatedAccount(accountId, AccountRole.DOCTOR, null); }

    void activeDoctor() {
        when(accounts.isActiveDoctorAccount(accountId)).thenReturn(true);
        when(identities.findDoctorProfileId(accountId)).thenReturn(Optional.of(doctorId));
    }

    @Test void responsibleCurrentDoctorCanReadAndUpdateHistoryUsingProfileNotAccountIdentity() {
        activeDoctor(); when(assignments.hasCurrentDoctorCare(doctorId, patientId)).thenReturn(true);
        when(profiles.findProfile(patientId)).thenReturn(Optional.of(new PatientProfileDirectory.ProfileRecord(
                patientId, null, "Patient", null, null, LocalDate.of(2000, 1, 1), "OTHER")));
        service.get(actor(), patientId);
        when(histories.insertInitial(eq(patientId), any(), any(), any(), any(), any(), any(), eq(accountId))).thenReturn(1);
        service.update(actor(), patientId, new UpdateMedicalHistoryRequest("", "", "", "", "", 0L));
        verify(assignments, times(2)).hasCurrentDoctorCare(doctorId, patientId);
        verify(assignments, never()).hasCurrentDoctorCare(accountId, patientId);
    }

    @Test void differentActiveDoctorAndNoMatchingActiveCareCannotReadOrWrite() {
        activeDoctor();
        assertEquals("ACCESS_DENIED", assertThrows(MedicalHistoryException.class, () -> service.get(actor(), patientId)).getMessage());
        assertEquals("ACCESS_DENIED", assertThrows(MedicalHistoryException.class,
                () -> service.update(actor(), patientId, new UpdateMedicalHistoryRequest("", "", "", "", "", 0L))).getMessage());
        verifyNoInteractions(profiles, histories);
    }

    @Test void inactiveDoctorIsDeniedBeforeVisitLookupEvenWhenProfileExists() {
        assertFalse(relation.isResponsibleDoctor(accountId, patientId));
        assertEquals("ACCESS_DENIED", assertThrows(MedicalHistoryException.class, () -> service.get(actor(), patientId)).getMessage());
        verifyNoInteractions(identities, assignments, profiles, histories);
    }

    @Test void missingProfileAndNullIdentityFailClosed() {
        when(accounts.isActiveDoctorAccount(accountId)).thenReturn(true);
        assertFalse(relation.isResponsibleDoctor(accountId, patientId));
        assertFalse(relation.isResponsibleDoctor(null, patientId)); assertFalse(relation.isResponsibleDoctor(accountId, null));
        verifyNoInteractions(assignments);
    }

    @Test void owningQueryRequiresOpenVisitMatchingPatientAndInProgressDoctorCare() throws Exception {
        String query = VisitCareAssignmentRepository.class.getMethod("hasCurrentDoctorCare", UUID.class, UUID.class)
                .getAnnotation(Query.class).value();
        for (String predicate : List.of("a.doctorProfileId = :doctorId", "v.patientProfileId = :patientId",
                "a.status = 'IN_PROGRESS'", "v.status = 'OPEN'")) assertTrue(query.contains(predicate));
        assertFalse(query.contains("RESERVED")); assertFalse(query.contains("COMPLETED"));
    }
}
