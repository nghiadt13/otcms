package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.PatientPrescriptionQueryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.content.service.ClinicContentService;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;

class PatientPrescriptionQueryServiceTest {
    final UUID accountId = UUID.randomUUID(), self = UUID.randomUUID(), dependent = UUID.randomUUID(),
            visit = UUID.randomUUID(), doctor = UUID.randomUUID(), id = UUID.randomUUID();
    final AuthenticatedAccount actor = new AuthenticatedAccount(accountId, AccountRole.PATIENT, null);
    final PatientProfileAccessService access = mock(PatientProfileAccessService.class);
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final DoctorProfileIdentityQueryService doctors = mock(DoctorProfileIdentityQueryService.class);
    final ClinicalPrescriptionRepository prescriptions = mock(ClinicalPrescriptionRepository.class);
    final ClinicalPrescriptionItemRepository items = mock(ClinicalPrescriptionItemRepository.class);
    final VisitClinicalContextPort visits = mock(VisitClinicalContextPort.class);
    final ClinicalExaminationHistoryQueryService exams = mock(ClinicalExaminationHistoryQueryService.class);
    final ClinicContentService clinic = mock(ClinicContentService.class);
    final PatientPrescriptionQueryService service = new PatientPrescriptionQueryServiceImpl(access, profiles, doctors,
            prescriptions, items, visits, exams, clinic);

    @BeforeEach void scope() { when(access.accessibleProfileIds(accountId)).thenReturn(Set.of(self, dependent)); }
    PatientProfileDirectory.ProfileRecord profile(UUID patientId) {
        return new PatientProfileDirectory.ProfileRecord(patientId, patientId.equals(self) ? accountId : null,
                "Patient", null, null, LocalDate.of(2000, 1, 1), "OTHER");
    }
    ClinicalPrescriptionJpaEntity header(UUID patientId, ClinicalPrescriptionStatus status) {
        var p = mock(ClinicalPrescriptionJpaEntity.class);
        when(p.getId()).thenReturn(id); when(p.getVisitId()).thenReturn(visit);
        when(p.getPatientProfileId()).thenReturn(patientId); when(p.getDoctorProfileId()).thenReturn(doctor);
        when(p.getStatus()).thenReturn(status); when(p.getIssuedAt()).thenReturn(Instant.parse("2026-11-05T00:00:00Z"));
        return p;
    }
    void code(String expected, Runnable action) {
        assertEquals(expected, assertThrows(ClinicalOperationException.class, action::run).code());
    }
    @Test void anonymousWrongRoleAndInactivePatientCannotRead() {
        code("MSG-40", () -> service.list(null, null, 0, 20));
        code("MSG-40", () -> service.detail(new AuthenticatedAccount(accountId, AccountRole.DOCTOR, null), id));
        when(access.accessibleProfileIds(accountId)).thenReturn(Set.of());
        code("MSG-40", () -> service.list(actor, null, 0, 20));
        verifyNoInteractions(prescriptions);
    }
    @Test void arbitraryPatientFilterRejectedBeforeDataLookup() {
        code("MSG-40", () -> service.list(actor, UUID.randomUUID(), 0, 20));
        verifyNoInteractions(prescriptions, profiles, items);
    }
    @Test void invalidPaginationRejected() {
        code("MSG-39", () -> service.list(actor, null, -1, 20));
        code("MSG-39", () -> service.list(actor, null, 0, 101));
        code("MSG-39", () -> service.list(actor, null, 0, 0));
        verifyNoInteractions(prescriptions);
    }
    @Test void listUsesAuthorizedIssuedDatabasePageAndBatchProjections() {
        var pageable = PageRequest.of(1, 2, Sort.by(Sort.Order.desc("issuedAt"), Sort.Order.desc("id")));
        var row = header(dependent, ClinicalPrescriptionStatus.ISSUED);
        when(prescriptions.findByPatientProfileIdInAndStatus(Set.of(self, dependent), ClinicalPrescriptionStatus.ISSUED, pageable))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 3));
        when(profiles.findProfiles(List.of(dependent))).thenReturn(List.of(profile(dependent)));
        when(doctors.findDoctorNames(List.of(doctor))).thenReturn(Map.of(doctor, "Doctor"));
        when(items.findByPrescriptionIdInOrderByPrescriptionIdAscLineNumberAsc(List.of(id)))
                .thenReturn(List.of(new ClinicalPrescriptionItemJpaEntity(id, 1, "Medicine", "Dose", "Frequency", "Usage")));
        var result = service.list(actor, null, 1, 2);
        assertEquals(3, result.totalItems()); assertEquals(2, result.totalPages());
        assertEquals(1, result.items().getFirst().itemCount());
        assertEquals("DEPENDENT", result.items().getFirst().patient().relationship());
        verify(items, never()).findByPrescriptionIdOrderByLineNumberAsc(any());
        verify(profiles, never()).findProfile(any());
    }
    @Test void outOfRangePagePreservesCountWithoutSecondaryQueries() {
        when(prescriptions.findByPatientProfileIdInAndStatus(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(3, 20), 1));
        assertEquals(1, service.list(actor, null, 3, 20).totalItems());
        verifyNoInteractions(profiles, doctors, items);
    }
    @Test void missingDraftAndUnrelatedDetailNeverExposeItems() {
        code("MSG-41", () -> service.detail(actor, id));
        var draft = header(self, ClinicalPrescriptionStatus.DRAFT);
        when(prescriptions.findById(id)).thenReturn(Optional.of(draft));
        code("MSG-41", () -> service.detail(actor, id));
        var unrelated = header(UUID.randomUUID(), ClinicalPrescriptionStatus.ISSUED);
        when(prescriptions.findById(id)).thenReturn(Optional.of(unrelated));
        code("MSG-40", () -> service.detail(actor, id));
        verifyNoInteractions(items, exams, clinic);
    }
    @Test void canonicalVisitPatientMismatchFailsClosed() {
        var row = header(self, ClinicalPrescriptionStatus.ISSUED);
        when(prescriptions.findById(id)).thenReturn(Optional.of(row));
        when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, dependent, doctor, false));
        code("MSG-41", () -> service.detail(actor, id));
        verifyNoInteractions(items, exams, clinic);
    }
    @Test void issuedDependentDetailDoesNotRequireHistoricalDoctorToRemainResponsible() {
        var row = header(dependent, ClinicalPrescriptionStatus.ISSUED);
        when(prescriptions.findById(id)).thenReturn(Optional.of(row));
        when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, dependent, UUID.randomUUID(), false));
        when(profiles.findProfile(dependent)).thenReturn(Optional.of(profile(dependent)));
        when(doctors.findDoctorNames(List.of(doctor))).thenReturn(Map.of(doctor, "Historical Doctor"));
        when(exams.findCompletedByVisitIds(List.of(visit))).thenReturn(Map.of(visit,
                new ClinicalExaminationHistoryQueryService.CompletedRecord(visit,
                        new ClinicalExaminationHistoryQueryService.Examination(null, null, null, null, null, null,
                                List.of(new ClinicalExaminationHistoryQueryService.Diagnosis("M25", fu.fa26.sep490.g58.otcms_be.clinical.model.DiagnosisType.PRIMARY))), null)));
        when(items.findByPrescriptionIdOrderByLineNumberAsc(id)).thenReturn(List.of(
                new ClinicalPrescriptionItemJpaEntity(id, 1, "Medicine", "Dose", "Frequency", "Usage")));
        var result = service.detail(actor, id);
        assertEquals("M25", result.diagnosis()); assertEquals("Historical Doctor", result.doctorName());
        assertEquals(dependent, result.patient().id()); assertEquals("Medicine", result.items().getFirst().medicine());
        assertNull(result.clinicName());
    }
}
