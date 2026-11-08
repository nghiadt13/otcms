package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalMedicalDocumentType;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.validation.ClinicalMedicalDocumentFileValidator;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClinicalMedicalDocumentServiceImplTest {
    final MedicalDocumentFixture f = new MedicalDocumentFixture();
    void error(String code, org.junit.jupiter.api.function.Executable action) { assertEquals(code, assertThrows(ClinicalOperationException.class, action).code()); }

    @Test void responsibleDoctorUploadsMultipleDocumentsWithServerPatientLinkAndNoPayloadInMetadata() {
        var first = f.uploadReport(); var second = f.uploadReport();
        assertNotEquals(first.id(), second.id()); assertEquals(2, f.service.list(f.doctor(), f.visitId).size());
        assertEquals(f.patientId, first.patientProfileId()); assertEquals(f.visitId, first.visitId()); assertFalse(first.released()); assertEquals(1, first.version());
        assertArrayEquals(MedicalDocumentFixture.pdf("initial"), f.service.content(f.doctor(), first.id()).bytes());
        assertEquals(first, f.service.get(f.doctor(), first.id()));
        assertThrows(UnsupportedOperationException.class, () -> f.service.list(f.doctor(), f.visitId).clear());
    }
    @Test void wrongDoctorMissingVisitAndDisallowedWorkFailBeforeWrites() {
        error("MSG-40", () -> f.service.upload(new AuthenticatedAccount(UUID.randomUUID(), AccountRole.DOCTOR, null), f.visitId,
                ClinicalMedicalDocumentType.EXTERNAL_REPORT, "Title", f.file("one")));
        f.exists = false; error("MSG-41", f::uploadReport);
        f.exists = true; f.allowed = false; error("MSG-42", f::uploadReport);
        verifyNoInteractions(f.documents, f.payloads);
    }
    @Test void receptionistCanAddButCannotCorrectOrReleaseAndOnlyReadsClinicReports() {
        var outside = f.service.upload(f.receptionist(), f.visitId, ClinicalMedicalDocumentType.EXTERNAL_REFERRAL, "Referral", f.file("outside"));
        var clinic = f.service.upload(f.receptionist(), f.visitId, ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT, "Clinic", f.file("clinic"));
        assertEquals(1, f.service.list(f.receptionist(), f.visitId).size()); assertEquals(clinic.id(), f.service.get(f.receptionist(), clinic.id()).id());
        error("MSG-40", () -> f.service.get(f.receptionist(), outside.id()));
        error("MSG-40", () -> f.service.update(f.receptionist(), f.visitId, clinic.id(), 1L, "Other", null));
        error("MSG-40", () -> f.service.release(f.receptionist(), f.visitId, clinic.id(), 1L));
        f.open = false; error("MSG-42", () -> f.service.get(f.receptionist(), clinic.id()));
    }
    @Test void inactiveReceptionistAndNonUploadRolesCannotAddDocuments() {
        when(f.receptionists.isActiveReceptionistAccount(f.receptionistAccountId)).thenReturn(false);
        error("MSG-40", () -> f.service.upload(f.receptionist(), f.visitId, ClinicalMedicalDocumentType.EXTERNAL_REPORT, "x", f.file("x")));
        error("MSG-40", () -> f.service.upload(f.patient(), f.visitId, ClinicalMedicalDocumentType.EXTERNAL_REPORT, "x", f.file("x")));
        error("MSG-40", () -> f.service.upload(null, f.visitId, ClinicalMedicalDocumentType.EXTERNAL_REPORT, "x", f.file("x")));
        verifyNoInteractions(f.documents, f.payloads);
    }
    @Test void obviousNonPdfAndMismatchedMimeAreRejectedDespitePdfFilename() {
        error("MSG-47", () -> f.service.upload(f.doctor(), f.visitId, ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT, "x",
                new DocumentFile("report.pdf", "application/pdf", "<html>not PDF</html>".getBytes())));
        error("MSG-47", () -> f.service.upload(f.doctor(), f.visitId, ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT, "x",
                new DocumentFile("report.pdf", "image/png", MedicalDocumentFixture.pdf("fake"))));
        verifyNoInteractions(f.documents, f.payloads);
    }
    @ParameterizedTest @ValueSource(strings = {"png", "jpeg"})
    void externalDocumentsAcceptExistingSupportedImageTypes(String format) throws Exception {
        var image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output = new java.io.ByteArrayOutputStream(); assertTrue(javax.imageio.ImageIO.write(image, format, output));
        var view = f.service.upload(f.doctor(), f.visitId, ClinicalMedicalDocumentType.EXTERNAL_REPORT, "Outside report",
                new DocumentFile("scan." + format, "image/" + format, output.toByteArray()));
        assertEquals("image/" + format, view.mediaType());
    }
    @Test void invalidFilesMetadataAndMissingVersionAreRejected() {
        for (byte[] content : Arrays.asList(null, new byte[0], new byte[ClinicalMedicalDocumentFileValidator.MAX_BYTES + 1]))
            error("MSG-47", () -> f.service.upload(f.doctor(), f.visitId, ClinicalMedicalDocumentType.EXTERNAL_REPORT, "x", new DocumentFile("x.pdf", "application/pdf", content)));
        error("MSG-47", () -> f.service.upload(f.doctor(), f.visitId, ClinicalMedicalDocumentType.EXTERNAL_REPORT, "x", new DocumentFile("x.exe", "application/octet-stream", new byte[100])));
        error("MSG-39", () -> f.service.upload(f.doctor(), f.visitId, null, "x", f.file("x")));
        error("MSG-39", () -> f.service.upload(f.doctor(), f.visitId, ClinicalMedicalDocumentType.EXTERNAL_REPORT, " ", f.file("x")));
        error("MSG-39", () -> f.service.update(f.doctor(), f.visitId, UUID.randomUUID(), null, "x", null));
        verifyNoInteractions(f.documents, f.payloads);
    }
    @Test void filenameIsBasenameAndContentBytesAreDefensivelyCopied() {
        byte[] source = MedicalDocumentFixture.pdf("file");
        var file = new DocumentFile("C:\\fakepath\\report\r\n.pdf", "application/pdf", source); source[0] = 0;
        var view = f.service.upload(f.doctor(), f.visitId, ClinicalMedicalDocumentType.EXTERNAL_REPORT, "Outside", file);
        assertEquals("report.pdf", view.originalFilename());
        var content = f.service.content(f.doctor(), view.id()); byte[] copy = content.bytes(); copy[0] = 0;
        assertEquals('%', content.bytes()[0]);
    }
    @Test void replacementAndMetadataOnlyUpdatesPreserveLinkageAndAdvanceVersion() {
        var first = f.uploadReport();
        var updated = f.service.update(f.doctor(), f.visitId, first.id(), 1L, "Corrected", f.file("replacement"));
        assertEquals(2, updated.version()); assertEquals(first.patientProfileId(), updated.patientProfileId());
        assertArrayEquals(MedicalDocumentFixture.pdf("replacement"), f.service.content(f.doctor(), first.id()).bytes());
        clearInvocations(f.payloads);
        assertEquals(3, f.service.update(f.doctor(), f.visitId, first.id(), 2L, "New title", null).version());
        verifyNoInteractions(f.payloads);
    }
    @Test void staleVersionOrLostCasDoesNotWritePayload() {
        var first = f.uploadReport(); clearInvocations(f.payloads);
        error("MSG-42", () -> f.service.update(f.doctor(), f.visitId, first.id(), 2L, "bad", f.file("bad")));
        doReturn(0).when(f.documents).replace(any(), anyLong(), any(), any(), any(), anyLong(), any());
        error("MSG-42", () -> f.service.update(f.doctor(), f.visitId, first.id(), 1L, "lost", f.file("lost")));
        verifyNoInteractions(f.payloads); assertEquals("Report", f.rows.get(first.id()).title);
    }
    @Test void reportReleaseNeedsCompletedExaminationAndMakesReportImmutable() {
        var first = f.uploadReport(); error("MSG-42", () -> f.service.release(f.doctor(), f.visitId, first.id(), 1L));
        f.completed(); var released = f.service.release(f.doctor(), f.visitId, first.id(), 1L);
        assertTrue(released.released()); assertEquals(f.now, released.releasedAt()); assertEquals(2, released.version());
        clearInvocations(f.payloads);
        error("MSG-42", () -> f.service.update(f.doctor(), f.visitId, first.id(), 2L, "bad", f.file("bad")));
        error("MSG-42", () -> f.service.release(f.doctor(), f.visitId, first.id(), 2L)); verifyNoInteractions(f.payloads);
    }
    @Test void externalReleaseDoesNotInventExaminationPrerequisite() {
        var external = f.service.upload(f.doctor(), f.visitId, ClinicalMedicalDocumentType.EXTERNAL_REPORT, "External", f.file("external"));
        assertTrue(f.service.release(f.doctor(), f.visitId, external.id(), 1L).released()); verifyNoInteractions(f.examinations);
    }
    @Test void patientSelfAndConfirmedDependentReadOnlyExplicitlyReleasedDocumentsEvenOnClosedVisit() {
        var doc = f.uploadReport(); error("MSG-41", () -> f.service.content(f.patient(), doc.id()));
        assertTrue(f.service.list(f.patient(), f.visitId).isEmpty());
        f.open = false; error("MSG-41", () -> f.service.get(f.patient(), doc.id())); f.open = true;
        f.completed(); f.service.release(f.doctor(), f.visitId, doc.id(), 1L); f.open = false;
        assertArrayEquals(MedicalDocumentFixture.pdf("initial"), f.service.content(f.patient(), doc.id()).bytes());
        f.dependent(true); assertTrue(f.service.get(f.patient(), doc.id()).released()); assertEquals(1, f.service.list(f.patient(), f.visitId).size());
    }
    @Test void unrelatedRevokedOrInactivePatientDoesNotReadPayload() {
        var doc = f.uploadReport(); f.completed(); f.service.release(f.doctor(), f.visitId, doc.id(), 1L);
        f.dependent(false); clearInvocations(f.payloads);
        error("MSG-40", () -> f.service.content(f.patient(), doc.id()));
        when(f.patientAccounts.isActivePatient(f.patientAccountId)).thenReturn(false);
        error("MSG-40", () -> f.service.get(f.patient(), doc.id())); verifyNoInteractions(f.payloads);
    }
    @Test void pastDocumentReadRequiresRealCurrentPatientCareAndDoesNotGrantMutation() {
        var doc = f.uploadReport(); f.open = false;
        error("MSG-40", () -> f.service.get(f.doctor(), doc.id()));
        when(f.care.hasCurrentCare(f.doctorId, f.patientId)).thenReturn(true);
        assertEquals(doc.id(), f.service.get(f.doctor(), doc.id()).id());
        error("MSG-42", () -> f.service.update(f.doctor(), f.visitId, doc.id(), 1L, "bad", null));
    }
    @Test void documentCannotBeMutatedThroughAnotherVisitAndMissingContentIsNotSuccessfulEmptyData() {
        var doc = f.uploadReport();
        error("MSG-41", () -> f.service.update(f.doctor(), UUID.randomUUID(), doc.id(), 1L, "bad", f.file("bad")));
        error("MSG-41", () -> f.service.get(f.doctor(), UUID.randomUUID()));
        f.bytes.clear(); error("DEPENDENCY_UNAVAILABLE", () -> f.service.content(f.doctor(), doc.id()));
    }
    @Test void databasePayloadFailurePropagatesAfterHeaderWithinTransactionRatherThanBeingSwallowed() {
        var doc = f.uploadReport(); doThrow(new org.springframework.dao.DataAccessResourceFailureException("Unavailable")).when(f.payloads).put(any(), any());
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> f.service.update(f.doctor(), f.visitId, doc.id(), 1L, "Correct", f.file("correct")));
        // No database rollback is claimed from these repository doubles.
    }
    @Test void competingReplacementsHaveOneCompleteWinner() throws Exception {
        var doc = f.uploadReport(); var results = race(
                () -> f.service.update(f.doctor(), f.visitId, doc.id(), 1L, "A", f.file("A")),
                () -> f.service.update(f.doctor(), f.visitId, doc.id(), 1L, "B", f.file("B")));
        assertEquals(1, results.stream().filter(DocumentView.class::isInstance).count()); assertTrue(results.contains("MSG-42"));
        var current = f.service.get(f.doctor(), doc.id()); assertEquals(2, current.version());
        assertArrayEquals(MedicalDocumentFixture.pdf(current.title()), f.service.content(f.doctor(), doc.id()).bytes());
    }
    @Test void releaseRacingReplacementHasOneWinnerAndPreservesPublishedBytes() throws Exception {
        var doc = f.uploadReport(); f.completed();
        var results = race(() -> f.service.release(f.doctor(), f.visitId, doc.id(), 1L),
                () -> f.service.update(f.doctor(), f.visitId, doc.id(), 1L, "Replacement", f.file("replacement")));
        assertEquals(1, results.stream().filter(DocumentView.class::isInstance).count()); assertTrue(results.contains("MSG-42"));
        var current = f.service.get(f.doctor(), doc.id());
        assertArrayEquals(MedicalDocumentFixture.pdf(current.released() ? "initial" : "replacement"), f.service.content(f.doctor(), doc.id()).bytes());
    }
    private List<Object> race(Callable<DocumentView> a, Callable<DocumentView> b) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return outcome(a); });
            var second = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return outcome(b); });
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }
    private Object outcome(Callable<DocumentView> action) throws Exception { try { return action.call(); } catch (ClinicalOperationException failure) { return failure.code(); } }
}
