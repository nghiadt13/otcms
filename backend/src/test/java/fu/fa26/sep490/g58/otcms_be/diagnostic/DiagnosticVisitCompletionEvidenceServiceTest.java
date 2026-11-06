package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticVisitCompletionEvidenceServiceImpl;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;

class DiagnosticVisitCompletionEvidenceServiceTest {
    final UUID visit = UUID.randomUUID(), patient = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-05T08:00:00Z");
    final DiagnosticRequestJpaRepository requests = mock(DiagnosticRequestJpaRepository.class);
    final DiagnosticAttemptJpaRepository attempts = mock(DiagnosticAttemptJpaRepository.class);
    final DiagnosticFindingsVersionJpaRepository findings = mock(DiagnosticFindingsVersionJpaRepository.class);
    final DiagnosticReportJpaRepository reports = mock(DiagnosticReportJpaRepository.class);
    final DiagnosticVisitCompletionEvidenceService service = new DiagnosticVisitCompletionEvidenceServiceImpl(requests, attempts, findings, reports);
    final List<DiagnosticRequestJpaEntity> requestRows = new ArrayList<>();
    final List<DiagnosticAttemptJpaEntity> attemptRows = new ArrayList<>();
    final List<DiagnosticFindingsVersionJpaEntity> findingRows = new ArrayList<>();
    final List<DiagnosticReportJpaEntity> reportRows = new ArrayList<>();

    @BeforeEach void queries() {
        when(requests.findAllByServiceOrderIdInAndStatusNot(anyCollection(), eq(DiagnosticRequestStatus.CANCELLED)))
                .thenAnswer(c -> requestRows.stream().filter(q -> c.<Collection<UUID>>getArgument(0).contains(q.getServiceOrderId()) && q.getStatus() != DiagnosticRequestStatus.CANCELLED).toList());
        when(requests.findAllById(any())).thenAnswer(c -> requestRows.stream().filter(q -> ids(c.getArgument(0)).contains(q.getId())).toList());
        when(attempts.findAllById(any())).thenAnswer(c -> attemptRows.stream().filter(a -> ids(c.getArgument(0)).contains(a.getId())).toList());
        when(findings.findAllByRequestIdInAndState(anyCollection(), eq(DiagnosticFindingsVersionState.COMPLETED)))
                .thenAnswer(c -> findingRows.stream().filter(f -> c.<Collection<UUID>>getArgument(0).contains(f.getRequestId()) && f.getState() == DiagnosticFindingsVersionState.COMPLETED).toList());
        when(reports.findAllById(any())).thenAnswer(c -> reportRows.stream().filter(p -> ids(c.getArgument(0)).contains(p.getId())).toList());
    }
    Set<UUID> ids(Iterable<UUID> values) { Set<UUID> result = new HashSet<>(); values.forEach(result::add); return result; }
    Row seed() {
        UUID order = UUID.randomUUID(), request = UUID.randomUUID(), attempt = UUID.randomUUID(), report = UUID.randomUUID();
        String study = "confirmed-" + request;
        var req = new DiagnosticRequestJpaEntity(request, visit, patient, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "MRI", DiagnosticRequestStatus.REVIEWED, null, null, attempt);
        req.setServiceOrderId(order);
        var a = new DiagnosticAttemptJpaEntity(attempt, request, 1, UUID.randomUUID(), "ACQUISITION_STARTED", "IMAGING", null, now, study);
        var f = new DiagnosticFindingsVersionJpaEntity(UUID.randomUUID(), request, visit, study, 1, "private findings", UUID.randomUUID(), now, DiagnosticFindingsVersionState.COMPLETED, report, now);
        var p = new DiagnosticReportJpaEntity(report, request, visit, study, 1, "private-storage", "a".repeat(64), DiagnosticReportValidationStatus.VALIDATED, UUID.randomUUID(), now, now);
        requestRows.add(req); attemptRows.add(a); findingRows.add(f); reportRows.add(p);
        return new Row(order, req, a, f, p);
    }
    record Row(UUID order, DiagnosticRequestJpaEntity request, DiagnosticAttemptJpaEntity attempt, DiagnosticFindingsVersionJpaEntity finding, DiagnosticReportJpaEntity report) { }
    DiagnosticVisitCompletionEvidenceService.ImagingCompletionEvidence evidence(UUID... orders) { return service.getCompletionEvidence(visit, patient, List.of(orders)); }
    void change(Object entity, String field, Object value) { ReflectionTestUtils.setField(entity, field, value); }
    @Test void emptyRequiredSetIsCompleteWithoutPersistence() {
        assertTrue(evidence().allComplete()); assertTrue(service.findCompletedRequestIds(List.of()).isEmpty());
        verifyNoInteractions(requests, attempts, findings, reports);
    }
    @Test void canonicalReleasedFindingsCompleteExactlyTheRequestedOrder() {
        var row = seed(); var result = evidence(row.order());
        assertTrue(result.allComplete()); assertEquals(Set.of(row.order()), result.completedServiceOrderIds());
        assertEquals(visit, result.visitId()); assertEquals(patient, result.patientId());
    }
    @Test void missingRequestFailsClosed() { assertFalse(evidence(UUID.randomUUID()).allComplete()); verifyNoInteractions(attempts, findings, reports); }
    @ParameterizedTest @EnumSource(value = DiagnosticRequestStatus.class, names = "REVIEWED", mode = EnumSource.Mode.EXCLUDE)
    void statusAloneNeverProvesCompletion(DiagnosticRequestStatus status) {
        var row = seed(); row.request().setStatus(status); assertFalse(evidence(row.order()).allComplete());
        verifyNoInteractions(attempts, findings, reports);
    }
    @ParameterizedTest @ValueSource(strings = {"visitId", "patientId", "serviceOrderId"})
    void wrongCanonicalRequestLinkageRejected(String field) {
        var row = seed(); change(row.request(), field, UUID.randomUUID()); assertFalse(evidence(row.order()).allComplete());
        verifyNoInteractions(attempts, findings, reports);
    }
    @Test void noCurrentAttemptRejected() { var row = seed(); change(row.request(), "currentAttemptId", null); assertFalse(evidence(row.order()).allComplete()); }
    @Test void missingCurrentAttemptRejected() { var row = seed(); attemptRows.clear(); assertFalse(evidence(row.order()).allComplete()); }
    @Test void attemptFromAnotherRequestRejected() { var row = seed(); change(row.attempt(), "requestId", UUID.randomUUID()); assertFalse(evidence(row.order()).allComplete()); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = " ")
    void noConfirmedStudyRejected(String study) { var row = seed(); row.attempt().setStudyRef(study); assertFalse(evidence(row.order()).allComplete()); }
    @Test void reviewedWithOnlyDraftFindingsRejected() { var row = seed(); change(row.finding(), "state", DiagnosticFindingsVersionState.DRAFT); assertFalse(evidence(row.order()).allComplete()); }
    @Test void completedStateWithoutCompletionTimeRejected() { var row = seed(); change(row.finding(), "completedAt", null); assertFalse(evidence(row.order()).allComplete()); }
    @Test void missingRequiredReportIdRejected() { var row = seed(); change(row.finding(), "requiredPdfReportId", null); assertFalse(evidence(row.order()).allComplete()); }
    @Test void missingRequiredReportRejected() { var row = seed(); reportRows.clear(); assertFalse(evidence(row.order()).allComplete()); }
    @ParameterizedTest @EnumSource(value = DiagnosticReportValidationStatus.class, names = "VALIDATED", mode = EnumSource.Mode.EXCLUDE)
    void unvalidatedReportRejected(DiagnosticReportValidationStatus status) { var row = seed(); row.report().setValidationStatus(status); assertFalse(evidence(row.order()).allComplete()); }
    @Test void missingValidationTimeRejected() { var row = seed(); row.report().setValidatedAt(null); assertFalse(evidence(row.order()).allComplete()); }
    @Test void reportNotReleasedRejected() { var row = seed(); row.report().setReleasedAt(null); assertFalse(evidence(row.order()).allComplete()); }
    @ParameterizedTest @ValueSource(strings = {"requestId", "visitId", "requiredPdfReportId"})
    void wrongFindingsLinkageRejected(String field) { var row = seed(); change(row.finding(), field, UUID.randomUUID()); assertFalse(evidence(row.order()).allComplete()); }
    @Test void staleFindingsStudyRejected() { var row = seed(); change(row.finding(), "studyRef", "old-study"); assertFalse(evidence(row.order()).allComplete()); }
    @ParameterizedTest @ValueSource(strings = {"requestId", "visitId", "id"})
    void wrongReportLinkageRejected(String field) { var row = seed(); change(row.report(), field, UUID.randomUUID()); assertFalse(evidence(row.order()).allComplete()); }
    @Test void wrongReportStudyRejected() { var row = seed(); change(row.report(), "studyRef", "other-study"); assertFalse(evidence(row.order()).allComplete()); }
    @Test void wrongReportVersionRejected() { var row = seed(); change(row.report(), "reportVersion", 2); assertFalse(evidence(row.order()).allComplete()); }
    @Test void privateDraftCorrectionDoesNotRevokeEffectivePublishedVersion() {
        var row = seed();
        findingRows.add(new DiagnosticFindingsVersionJpaEntity(UUID.randomUUID(), row.request().getId(), visit, row.attempt().getStudyRef(), 2, "private draft correction", UUID.randomUUID(), now, DiagnosticFindingsVersionState.DRAFT, null, null));
        assertTrue(evidence(row.order()).allComplete());
    }
    @Test void newerCompletedVersionCannotFallBackToOlderReleasedReport() {
        var row = seed();
        findingRows.add(new DiagnosticFindingsVersionJpaEntity(UUID.randomUUID(), row.request().getId(), visit, row.attempt().getStudyRef(), 2, "newer", UUID.randomUUID(), now, DiagnosticFindingsVersionState.COMPLETED, UUID.randomUUID(), now));
        assertFalse(evidence(row.order()).allComplete());
    }
    @Test void twoRequiredOrdersMustBothBeComplete() {
        var a = seed(); var b = seed(); assertTrue(evidence(a.order(), b.order()).allComplete());
        b.report().setReleasedAt(null); var result = evidence(a.order(), b.order());
        assertFalse(result.allComplete()); assertEquals(Set.of(a.order()), result.completedServiceOrderIds());
    }
    @Test void canceledRequestHistoryDoesNotCompeteWithTheSingleLiveRequest() {
        var row = seed(); var old = seed(); old.request().setServiceOrderId(row.order()); old.request().setStatus(DiagnosticRequestStatus.CANCELLED);
        assertTrue(evidence(row.order()).allComplete());
    }
    @Test void multipleLiveRequestsFailClosedEvenAcrossVisits() {
        var row = seed(); var duplicate = seed(); duplicate.request().setServiceOrderId(row.order()); change(duplicate.request(), "visitId", UUID.randomUUID());
        assertFalse(evidence(row.order()).allComplete()); verifyNoInteractions(attempts, findings, reports);
    }
    @Test void oneHundredOrdersUseFourBatchQueriesAndImmutableOutput() {
        List<UUID> orders = new ArrayList<>(); for (int i = 0; i < 100; i++) orders.add(seed().order());
        var result = service.getCompletionEvidence(visit, patient, orders); assertTrue(result.allComplete()); assertEquals(100, result.completedServiceOrderIds().size());
        verify(requests, times(1)).findAllByServiceOrderIdInAndStatusNot(anyCollection(), eq(DiagnosticRequestStatus.CANCELLED));
        verify(attempts, times(1)).findAllById(any()); verify(findings, times(1)).findAllByRequestIdInAndState(anyCollection(), eq(DiagnosticFindingsVersionState.COMPLETED));
        verify(reports, times(1)).findAllById(any()); verifyNoMoreInteractions(requests, attempts, findings, reports);
        assertThrows(UnsupportedOperationException.class, () -> result.completedServiceOrderIds().clear()); orders.clear(); assertEquals(100, result.requiredServiceOrderIds().size());
    }
    @Test void requestStatusProjectionAlsoSupportsLegacyUnlinkedRequests() {
        var row = seed(); row.request().setServiceOrderId(null);
        assertEquals(Set.of(row.request().getId()), service.findCompletedRequestIds(List.of(row.request().getId())));
    }
    @Test void completedFindingsCannotBeChangedBackToDraftBySupportedEntityCommands() {
        var row = seed();
        assertThrows(IllegalStateException.class, () -> row.finding().reviseDraft("replacement", now));
        assertThrows(IllegalStateException.class, () -> row.finding().setCorrectionReason("replacement"));
        assertThrows(IllegalStateException.class, () -> row.finding().markReleased(UUID.randomUUID(), now));
        assertTrue(evidence(row.order()).allComplete());
    }
    @Test void newerCompletedAndReleasedVersionRemainsCompleteDuringCorrectionPublication() {
        var row = seed(); UUID newReport = UUID.randomUUID();
        findingRows.add(new DiagnosticFindingsVersionJpaEntity(UUID.randomUUID(), row.request().getId(), visit, row.attempt().getStudyRef(), 2,
                "Correction", UUID.randomUUID(), now, DiagnosticFindingsVersionState.COMPLETED, newReport, now));
        reportRows.add(new DiagnosticReportJpaEntity(newReport, row.request().getId(), visit, row.attempt().getStudyRef(), 2,
                "private-correction", "b".repeat(64), DiagnosticReportValidationStatus.VALIDATED, UUID.randomUUID(), now, now));
        assertTrue(evidence(row.order()).allComplete());
    }
    @Test void repositoryFailurePropagatesRatherThanReturningEmptyProof() {
        when(requests.findAllByServiceOrderIdInAndStatusNot(anyCollection(), any())).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> evidence(UUID.randomUUID()));
    }
    @Test void nullInputsRejectedBeforePersistence() {
        assertThrows(NullPointerException.class, () -> service.getCompletionEvidence(null, patient, List.of()));
        assertThrows(NullPointerException.class, () -> service.getCompletionEvidence(visit, null, List.of()));
        assertThrows(NullPointerException.class, () -> service.getCompletionEvidence(visit, patient, null));
        assertThrows(NullPointerException.class, () -> service.findCompletedRequestIds(Arrays.asList((UUID) null)));
        verifyNoInteractions(requests, attempts, findings, reports);
    }
}
