package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalExaminationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentService.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** UUID-isolated dedicated test DB fixtures; no database install/start/reset or user-data deletion. */
@SpringBootTest @LocalPostgresTest
class ClinicalMedicalDocumentPostgresIntegrationTest {
    @MockitoBean VisitClinicalContextPort visits;
    @MockitoBean fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService visitReads;
    @MockitoBean DoctorProfileIdentityQueryService doctors;
    @MockitoBean fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService visitSources;
    @MockitoSpyBean ClinicalMedicalDocumentContentStore contents;
    @Autowired ClinicalMedicalDocumentRepository documents;
    @Autowired ClinicalExaminationRepository examinations;
    @Autowired ClinicalMedicalDocumentService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    final UUID visitId = UUID.randomUUID(), patientId = UUID.randomUUID(), doctorId = UUID.randomUUID(), accountId = UUID.randomUUID();
    AuthenticatedAccount actor() { return new AuthenticatedAccount(accountId, AccountRole.DOCTOR, null); }
    DocumentFile file(String marker) { return new DocumentFile("report.pdf", "application/pdf", MedicalDocumentFixture.pdf(marker)); }
    @BeforeEach void care() {
        when(doctors.findDoctorProfileId(accountId)).thenReturn(Optional.of(doctorId));
        when(visitSources.find(visitId)).thenReturn(Optional.of(new fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService.Source(
                visitId, patientId, "OPEN", Instant.now(), "", "")));
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visitId, patientId, doctorId, true));
        when(visitReads.findClinicalCareContext(visitId)).thenReturn(Optional.of(
                new fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService.ClinicalCareContext(
                        visitId, patientId, UUID.randomUUID(), doctorId, true, true)));
    }
    DocumentView upload() { return service.upload(actor(), visitId, ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT, "Initial", file("initial")); }
    @Test void schemaAndBinaryPayloadPersistWithoutMetadataPayloadCoupling() {
        var doc = upload();
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class, this::upload).code());
        assertArrayEquals(MedicalDocumentFixture.pdf("initial"), contents.get(visitId, doc.id()).orElseThrow());
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.columns where table_schema = current_schema()
                and table_name = 'clinical_medical_document_payloads' and data_type = 'bytea'
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from medical_documents where visit_id = ?", Integer.class, visitId));
        assertEquals(1, doc.version());
    }
    @Test void expectedVersionAndAtomicReplacementPersistTogether() {
        var doc = upload(); var changed = service.update(actor(), visitId, doc.id(), 1L, "Changed", file("changed"));
        assertEquals(2, changed.version()); assertEquals("Changed", service.get(actor(), doc.id()).title());
        assertArrayEquals(MedicalDocumentFixture.pdf("changed"), contents.get(visitId, doc.id()).orElseThrow());
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class,
                () -> service.update(actor(), visitId, doc.id(), 1L, "Stale", file("stale"))).code());
    }
    @Test void concurrentReplacementsHaveOneMetadataPayloadWinner() throws Exception {
        var doc = upload(); var results = race(() -> service.update(actor(), visitId, doc.id(), 1L, "A", file("A")),
                () -> service.update(actor(), visitId, doc.id(), 1L, "B", file("B")));
        assertEquals(1, results.stream().filter(DocumentView.class::isInstance).count()); assertTrue(results.contains("MSG-42"));
        var metadata = service.get(actor(), doc.id()); assertEquals(2, metadata.version());
        assertArrayEquals(MedicalDocumentFixture.pdf(metadata.title()), contents.get(visitId, doc.id()).orElseThrow());
    }
    @Test void payloadWriteFailureRollsBackBothMetadataAndDeletedOrChangedBytes() {
        var doc = upload();
        doReturn(false).when(contents).put(eq(visitId), eq(doc.id()), any(), any());
        assertEquals("DEPENDENCY_UNAVAILABLE", assertThrows(ClinicalOperationException.class,
                () -> service.update(actor(), visitId, doc.id(), 1L, "Must rollback", file("failed"))).code());
        var stored = documents.findById(doc.id()).orElseThrow(); assertEquals(1, stored.getVersion()); assertEquals("Initial", stored.getTitle());
        assertArrayEquals(MedicalDocumentFixture.pdf("initial"), contents.get(visitId, doc.id()).orElseThrow());
    }
    @Test void releasePreventsServiceAndRepositoryReplacement() {
        var doc = upload(); completed(); var released = service.release(actor(), visitId, doc.id(), 1L); assertTrue(released.released());
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class,
                () -> service.update(actor(), visitId, doc.id(), 2L, "Bad", file("bad"))).code());
        new TransactionTemplate(transactions).execute(status -> {
            assertEquals(0, documents.replace(doc.id(), 2L, "Bad", "bad.pdf", "application/pdf", 10, Instant.now()));
            verify(contents, times(1)).put(any(), any(), any(), any()); return null;
        });
        assertArrayEquals(MedicalDocumentFixture.pdf("initial"), contents.get(visitId, doc.id()).orElseThrow());
    }
    @Test void releaseAndReplacementRaceHasOneWinner() throws Exception {
        var doc = upload(); completed(); var results = race(() -> service.release(actor(), visitId, doc.id(), 1L),
                () -> service.update(actor(), visitId, doc.id(), 1L, "New", file("new")));
        assertEquals(1, results.stream().filter(DocumentView.class::isInstance).count()); assertTrue(results.contains("MSG-42"));
        var metadata = service.get(actor(), doc.id());
        assertArrayEquals(MedicalDocumentFixture.pdf(metadata.released() ? "initial" : "new"), contents.get(visitId, doc.id()).orElseThrow());
    }
    private void completed() {
        examinations.saveAndFlush(ClinicalExaminationJpaEntity.create(visitId, "reason", "symptoms", "findings", null, null, null,
                ClinicalExaminationStatus.COMPLETED, Instant.now()));
    }
    private List<Object> race(Callable<DocumentView> a, Callable<DocumentView> b) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return outcome(a); });
            var second = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return outcome(b); });
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }
    private Object outcome(Callable<DocumentView> action) throws Exception { try { return action.call(); } catch (ClinicalOperationException failure) { return failure.code(); } }
}
