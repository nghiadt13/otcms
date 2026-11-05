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
    @MockitoSpyBean ClinicalMedicalDocumentPayloadRepository payloads;
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
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visitId, patientId, doctorId, true));
        when(visitReads.findClinicalCareContext(visitId)).thenReturn(Optional.of(
                new fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService.ClinicalCareContext(
                        visitId, patientId, UUID.randomUUID(), doctorId, true, true)));
    }
    DocumentView upload() { return service.upload(actor(), visitId, ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT, "Initial", file("initial")); }
    @Test void schemaAndBinaryPayloadPersistWithoutMetadataPayloadCoupling() {
        var doc = upload(); var second = upload(); assertNotEquals(doc.id(), second.id());
        assertArrayEquals(MedicalDocumentFixture.pdf("initial"), jdbc.queryForObject("select content from clinical_medical_document_payloads where document_id = ?", byte[].class, doc.id()));
        assertEquals("bytea", jdbc.queryForObject("""
                select data_type from information_schema.columns where table_schema = current_schema()
                and table_name = 'clinical_medical_document_payloads' and column_name = 'content'
                """, String.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from clinical_medical_documents where visit_id = ?", Integer.class, visitId));
        assertEquals(1, doc.version());
    }
    @Test void expectedVersionAndAtomicReplacementPersistTogether() {
        var doc = upload(); var changed = service.update(actor(), visitId, doc.id(), 1L, "Changed", file("changed"));
        assertEquals(2, changed.version()); assertEquals("Changed", service.get(actor(), doc.id()).title());
        assertArrayEquals(MedicalDocumentFixture.pdf("changed"), payloads.findById(doc.id()).orElseThrow().getContent());
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class,
                () -> service.update(actor(), visitId, doc.id(), 1L, "Stale", file("stale"))).code());
    }
    @Test void concurrentReplacementsHaveOneMetadataPayloadWinner() throws Exception {
        var doc = upload(); var results = race(() -> service.update(actor(), visitId, doc.id(), 1L, "A", file("A")),
                () -> service.update(actor(), visitId, doc.id(), 1L, "B", file("B")));
        assertEquals(1, results.stream().filter(DocumentView.class::isInstance).count()); assertTrue(results.contains("MSG-42"));
        var metadata = service.get(actor(), doc.id()); assertEquals(2, metadata.version());
        assertArrayEquals(MedicalDocumentFixture.pdf(metadata.title()), payloads.findById(doc.id()).orElseThrow().getContent());
    }
    @Test void payloadWriteFailureRollsBackBothMetadataAndDeletedOrChangedBytes() {
        var doc = upload();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Injected fixture failure")).when(payloads).put(eq(doc.id()), any());
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> service.update(actor(), visitId, doc.id(), 1L, "Must rollback", file("failed")));
        var stored = documents.findById(doc.id()).orElseThrow(); assertEquals(1, stored.getVersion()); assertEquals("Initial", stored.getTitle());
        assertArrayEquals(MedicalDocumentFixture.pdf("initial"), payloads.findById(doc.id()).orElseThrow().getContent());
    }
    @Test void releasePreventsServiceAndRepositoryReplacement() {
        var doc = upload(); completed(); var released = service.release(actor(), visitId, doc.id(), 1L); assertTrue(released.released());
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class,
                () -> service.update(actor(), visitId, doc.id(), 2L, "Bad", file("bad"))).code());
        new TransactionTemplate(transactions).execute(status -> {
            assertEquals(0, documents.replace(doc.id(), 2L, "Bad", "bad.pdf", "application/pdf", 10, Instant.now()));
            assertEquals(0, payloads.put(doc.id(), MedicalDocumentFixture.pdf("bad"))); return null;
        });
        assertArrayEquals(MedicalDocumentFixture.pdf("initial"), payloads.findById(doc.id()).orElseThrow().getContent());
    }
    @Test void releaseAndReplacementRaceHasOneWinner() throws Exception {
        var doc = upload(); completed(); var results = race(() -> service.release(actor(), visitId, doc.id(), 1L),
                () -> service.update(actor(), visitId, doc.id(), 1L, "New", file("new")));
        assertEquals(1, results.stream().filter(DocumentView.class::isInstance).count()); assertTrue(results.contains("MSG-42"));
        var metadata = service.get(actor(), doc.id());
        assertArrayEquals(MedicalDocumentFixture.pdf(metadata.released() ? "initial" : "new"), payloads.findById(doc.id()).orElseThrow().getContent());
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
