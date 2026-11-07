package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.*;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.visit.repository.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/** Real Visit/Diagnostic/Clinical composition, doubled persistence and external Imaging contracts.
 * Does not claim PostgreSQL, Orthanc, secure viewer runtime, or queue-return execution. */
class DoctorImagingReviewIntegrationTest {
    AnnotationConfigWebApplicationContext context;
    final UUID visit = UUID.randomUUID(), patient = UUID.randomUUID(), account = UUID.randomUUID(), doctor = UUID.randomUUID();
    final UUID requestId = UUID.randomUUID(), attemptId = UUID.randomUUID();
    final String study = "confirmed-study";
    DiagnosticRequestJpaEntity request;
    final Map<UUID, DiagnosticFindingsVersionJpaEntity> versions = new HashMap<>();
    final Map<UUID, DiagnosticReportJpaEntity> reportRows = new HashMap<>();
    final Map<UUID, DiagnosticOutboxJpaEntity> events = new HashMap<>();
    final Map<UUID, VisitImagingResultReceiptJpaEntity> receipts = new HashMap<>();
    final Map<String, DiagnosticIdempotencyRecordJpaEntity> keys = new HashMap<>();
    <T> T bean(Class<T> type) { return context.getBean(type); }
    DiagnosticActor actor() { return DiagnosticActor.user(account, AccountRole.DOCTOR); }
    DiagnosticFindingsService findings() { return bean(DiagnosticFindingsService.class); }

    @BeforeEach void start() throws Exception {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(DoctorCoreProductionWiringTest.ProductionConfiguration.class); context.refresh();
        var profile = mock(DoctorProfileJpaEntity.class); when(profile.id()).thenReturn(doctor);
        when(bean(DoctorProfileRepository.class).findByAccountId(account)).thenReturn(Optional.of(profile));
        when(bean(AccountRepository.class).existsByIdAndAccountTypeAndStatus(eq(account), eq(AccountType.DOCTOR), any())).thenReturn(true);
        when(bean(ClinicVisitJpaRepository.class).findById(visit)).thenReturn(Optional.of(
                ClinicVisitJpaEntity.walkIn(visit, patient, null, account, Instant.EPOCH)));
        when(bean(VisitCareAssignmentRepository.class).findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visit, "IN_PROGRESS"))
                .thenReturn(Optional.of(VisitCareAssignmentJpaEntity.inProgress(UUID.randomUUID(), visit, doctor,
                        "ORTHOPEDIC", 30, Instant.EPOCH, Instant.EPOCH.plusSeconds(1800))));
        request = new DiagnosticRequestJpaEntity(requestId, visit, patient, account, UUID.randomUUID(), null,
                "MRI", DiagnosticRequestStatus.RESULT_AVAILABLE, null, null, attemptId);
        request.setCreatedAt(Instant.EPOCH);
        when(bean(DiagnosticRequestJpaRepository.class).findById(requestId)).thenReturn(Optional.of(request));
        when(bean(DiagnosticRequestJpaRepository.class).findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(bean(DiagnosticRequestJpaRepository.class).saveAndFlush(any())).thenAnswer(call -> {
            ReflectionTestUtils.setField(request, "version", request.getVersion() + 1); return request;
        });
        when(bean(DiagnosticAttemptJpaRepository.class).findByRequestIdAndId(requestId, attemptId)).thenReturn(Optional.of(
                new DiagnosticAttemptJpaEntity(attemptId, requestId, 1, UUID.randomUUID(), "ACQUISITION_STARTED", "IMAGING", null, Instant.EPOCH, study)));
        var findings = bean(DiagnosticFindingsVersionJpaRepository.class);
        when(findings.saveAndFlush(any())).thenAnswer(call -> { var row = (DiagnosticFindingsVersionJpaEntity) call.getArgument(0); versions.put(row.getId(), row); return row; });
        when(findings.findFirstByRequestIdOrderByVersionNumberDesc(requestId)).thenAnswer(call -> versions.values().stream().max(Comparator.comparingInt(DiagnosticFindingsVersionJpaEntity::getVersionNumber)));
        when(findings.findAllByRequestIdOrderByVersionNumberDesc(requestId)).thenAnswer(call -> new ArrayList<>(versions.values()));
        when(findings.findByIdAndRequestId(any(), eq(requestId))).thenAnswer(call -> Optional.ofNullable(versions.get(call.getArgument(0))));
        var reports = bean(DiagnosticReportJpaRepository.class);
        when(reports.saveAndFlush(any())).thenAnswer(call -> { var row = (DiagnosticReportJpaEntity) call.getArgument(0); reportRows.put(row.getId(), row); return row; });
        when(reports.findByIdAndRequestId(any(), eq(requestId))).thenAnswer(call -> Optional.ofNullable(reportRows.get(call.getArgument(0))));
        var outbox = bean(DiagnosticOutboxJpaRepository.class);
        when(outbox.save(any())).thenAnswer(call -> { var row = (DiagnosticOutboxJpaEntity) call.getArgument(0); events.put(row.getEventId(), row); return row; });
        when(outbox.findByEventId(any())).thenAnswer(call -> Optional.ofNullable(events.get(call.getArgument(0))));
        when(outbox.lockDueEvents(any(), any(), any())).thenAnswer(call -> events.values().stream().filter(e -> e.getStatus() == DiagnosticOutboxStatus.PENDING).toList());
        var idempotency = bean(DiagnosticIdempotencyRecordJpaRepository.class);
        when(idempotency.claimActorOperation(any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            String key = call.getArgument(4) + ":" + call.getArgument(5);
            if (keys.containsKey(key)) return 0;
            keys.put(key, new DiagnosticIdempotencyRecordJpaEntity(call.getArgument(0), account, null, visit, requestId,
                    call.getArgument(4), call.getArgument(5), call.getArgument(6), DiagnosticOperationOutcome.ALLOWED, new HashMap<>())); return 1;
        });
        when(idempotency.findActorOperation(any(), any(), any(), any(), any())).thenAnswer(call -> Optional.ofNullable(keys.get(call.getArgument(3) + ":" + call.getArgument(4))));
        // Bulk DML does not refresh a previously managed entity; do not hide that production behavior.
        when(idempotency.completeResult(any(), any())).thenReturn(1);
        var inbox = bean(VisitImagingResultReceiptRepository.class);
        when(inbox.insertReceipt(any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            UUID id = call.getArgument(0); if (receipts.containsKey(id)) return 0;
            var row = mock(VisitImagingResultReceiptJpaEntity.class);
            when(row.getEventId()).thenReturn(id); when(row.getRequestId()).thenReturn(call.getArgument(1));
            when(row.getVisitId()).thenReturn(call.getArgument(2)); when(row.getPatientProfileId()).thenReturn(call.getArgument(3));
            when(row.getEventType()).thenReturn(call.getArgument(4)); when(row.getPayloadSha256()).thenReturn(call.getArgument(5));
            receipts.put(id, row); return 1;
        });
        when(inbox.findById(any())).thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(0))));
    }
    @AfterEach void close() { context.close(); }
    void error(DiagnosticOperationOutcome expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(DiagnosticOperationException.class, action).outcome());
    }
    void review() { findings().review(actor(), requestId, "review", request.getVersion(), study, "test"); }
    DiagnosticFindingsView draft() { review(); return findings().saveDraft(actor(), requestId, "draft", request.getVersion(), "No fracture", "test"); }
    UUID report(int version, DiagnosticReportValidationStatus validation) {
        UUID id = UUID.randomUUID(); reportRows.put(id, new DiagnosticReportJpaEntity(id, requestId, visit, study, version,
                "private/report", "a".repeat(64), validation, account, Instant.EPOCH, null)); return id;
    }
    FindingsReleaseEvent release() {
        var draft = draft(); var report = report(draft.version(), DiagnosticReportValidationStatus.VALIDATED);
        findings().complete(actor(), requestId, "complete", request.getVersion(), draft.version(), report, study, "test");
        var event = events.values().iterator().next();
        return new FindingsReleaseEvent(event.getEventId(), requestId, visit, patient, study, draft.findingsVersionId(), report, "a".repeat(64));
    }
    DiagnosticOutboxJpaEntity ready(UUID targetVisit) {
        UUID id = UUID.randomUUID(); var row = new DiagnosticOutboxJpaEntity(UUID.randomUUID(), id, requestId, targetVisit,
                "IMAGING_RESULT_READY", Map.of("eventId", id.toString(), "requestId", requestId.toString(), "visitId", targetVisit.toString()),
                DiagnosticOutboxStatus.PENDING, 0, Instant.EPOCH, null, null); events.put(id, row); return row;
    }
    @Test void correctionAndCompletionReplayUseCompletedManagedPayloadAndPreserveReleaseGates() {
        var initial = draft();
        UUID firstReport = report(1, DiagnosticReportValidationStatus.VALIDATED);
        var old = versions.get(initial.findingsVersionId());
        old.markReleased(firstReport, Instant.EPOCH);
        reportRows.get(firstReport).setReleasedAt(Instant.EPOCH);
        long requestVersion = request.getVersion();
        var correction = findings().createCorrection(actor(), requestId, "correction", requestVersion,
                "Small effusion", "Image re-review", "test");
        var replay = findings().createCorrection(actor(), requestId, "correction", requestVersion,
                "Small effusion", "Image re-review", "replay");
        assertEquals(correction.findingsVersionId(), replay.findingsVersionId());
        assertEquals(2, replay.version()); assertEquals("PRIVATE", replay.releaseState());
        assertEquals(2, versions.size());
        UUID secondReport = report(2, DiagnosticReportValidationStatus.VALIDATED);
        findings().complete(actor(), requestId, "complete-correction", requestVersion, 2, secondReport, study, "test");
        assertEquals("RELEASE_PENDING", findings().complete(actor(), requestId, "complete-correction",
                requestVersion, 2, secondReport, study, "replay").releaseState());
        var imaging = mock(ImagingStudyPort.class);
        when(imaging.acceptFindingsRelease(any())).thenAnswer(call -> {
            var event = (FindingsReleaseEvent) call.getArgument(0);
            assertEquals(1, receipts.size()); // Owner ACK already persisted, second ACK not returned.
            assertEquals(DiagnosticFindingsVersionState.DRAFT, versions.get(correction.findingsVersionId()).getState());
            assertNull(reportRows.get(secondReport).getReleasedAt());
            return new ReleaseAcknowledgement(event.eventId(), requestId,
                    bean(DiagnosticResultEventQueryService.class).requireFindingsRelease(event).payloadSha256(), true);
        });
        context.getBeanFactory().registerSingleton("externalImagingCorrectionDouble", imaging);
        var retry = new DiagnosticEvidenceService.RetryPolicy(3, 1, 8);
        assertEquals(1, bean(DiagnosticEvidenceService.class).dispatchDueOutbox(10, retry).delivered());
        assertEquals(DiagnosticFindingsVersionState.COMPLETED, versions.get(correction.findingsVersionId()).getState());
        assertNotNull(reportRows.get(secondReport).getReleasedAt());
        assertEquals("RELEASED", findings().complete(actor(), requestId, "complete-correction",
                requestVersion, 2, secondReport, study, "released-replay").releaseState());
        assertEquals(0, bean(DiagnosticEvidenceService.class).dispatchDueOutbox(10, retry).delivered());
        verify(imaging, times(1)).acceptFindingsRelease(any());
        assertEquals("No fracture", old.getFindingsText());
        assertEquals(firstReport, old.getRequiredPdfReportId()); assertEquals(Instant.EPOCH, old.getCompletedAt());
        assertThrows(IllegalStateException.class, () -> old.reviseDraft("changed", Instant.now()));
        verify(bean(DiagnosticIdempotencyRecordJpaRepository.class), never()).completeResult(any(), any());
        verify(bean(DiagnosticIdempotencyRecordJpaRepository.class), atLeastOnce()).saveAndFlush(any());
    }

    @Test void currentDoctorReviewsAndIdenticalReplayIsSafe() {
        review(); assertEquals(DiagnosticRequestStatus.REVIEWED, request.getStatus());
        findings().review(actor(), requestId, "review", 0, study, "test"); assertEquals(1, request.getVersion());
        error(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT, () -> findings().review(actor(), requestId, "review", 0, "other", "test"));
    }
    @Test void inactiveDoctorCannotReviewOrReplay() {
        review(); when(bean(AccountRepository.class).existsByIdAndAccountTypeAndStatus(eq(account), eq(AccountType.DOCTOR), any())).thenReturn(false);
        error(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, () -> findings().review(actor(), requestId, "review", 0, study, "test"));
    }
    @Test void wrongDoctorCannotReviewOrWriteFindings() {
        var other = DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR);
        error(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, () -> findings().review(other, requestId, "x", 0, study, "test"));
        error(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, () -> findings().saveDraft(other, requestId, "y", 0, "findings", "test"));
    }
    @Test void formerRequestingDoctorLosesReviewAccessWhenCurrentDoctorChanges() {
        when(bean(VisitCareAssignmentRepository.class).findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visit, "IN_PROGRESS"))
                .thenReturn(Optional.of(VisitCareAssignmentJpaEntity.inProgress(UUID.randomUUID(), visit, UUID.randomUUID(), "ORTHOPEDIC", 30, Instant.EPOCH, Instant.EPOCH.plusSeconds(1800))));
        error(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, this::review);
    }
    @Test void mismatchedPatientDeniedBeforeReview() {
        when(bean(ClinicVisitJpaRepository.class).findById(visit)).thenReturn(Optional.of(ClinicVisitJpaEntity.walkIn(visit, UUID.randomUUID(), null, account, Instant.EPOCH)));
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, this::review);
    }
    @Test void closedVisitCannotReview() {
        var closed = mock(ClinicVisitJpaEntity.class); when(closed.getId()).thenReturn(visit); when(closed.getPatientProfileId()).thenReturn(patient); when(closed.getStatus()).thenReturn("CLOSED");
        when(bean(ClinicVisitJpaRepository.class).findById(visit)).thenReturn(Optional.of(closed));
        error(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE, this::review);
    }
    @ParameterizedTest @EnumSource(value = DiagnosticRequestStatus.class, names = {"PENDING", "IN_PROGRESS", "CANCELLED"})
    void nonReviewableStatesDenied(DiagnosticRequestStatus status) {
        request.setStatus(status);
        if (status != DiagnosticRequestStatus.IN_PROGRESS) request.setCurrentAttemptId(null);
        else when(bean(DiagnosticAttemptJpaRepository.class).findByRequestIdAndId(requestId, attemptId)).thenReturn(Optional.of(
                new DiagnosticAttemptJpaEntity(attemptId, requestId, 1, UUID.randomUUID(), "ACQUISITION_STARTED", "IMAGING", null, Instant.EPOCH, null)));
        error(DiagnosticOperationOutcome.STATE_CONFLICT, this::review);
    }
    @Test void matchedConfirmedStudyCoordinatesRealVisitThroughExistingOutbox() {
        request.setStatus(DiagnosticRequestStatus.IN_PROGRESS);
        var attempt = new DiagnosticAttemptJpaEntity(attemptId, requestId, 1, UUID.randomUUID(), "ACQUISITION_STARTED", "IMAGING", null, Instant.EPOCH, null);
        when(bean(DiagnosticAttemptJpaRepository.class).findByRequestIdAndId(requestId, attemptId)).thenReturn(Optional.of(attempt));
        var event = new StudyEvidence(UUID.randomUUID(), study, requestId, visit, patient, attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);
        var imaging = mock(ImagingStudyPort.class);
        when(imaging.confirmIngestedStudy(event.eventId(), study, requestId, visit, patient, attemptId, event.verificationEvidence())).thenReturn(event);
        context.getBeanFactory().registerSingleton("externalImagingConfirmationDouble", imaging);
        var result = bean(DiagnosticEvidenceService.class).linkConfirmedStudy(DiagnosticActor.trustedIntegration(), event, "test");
        assertEquals(DiagnosticRequestStatus.RESULT_AVAILABLE, result.status());
        verify(imaging).confirmIngestedStudy(event.eventId(), study, requestId, visit, patient, attemptId, event.verificationEvidence());
        assertEquals(1, bean(DiagnosticEvidenceService.class).dispatchDueOutbox(10,
                new DiagnosticEvidenceService.RetryPolicy(3, 1, 8)).delivered());
        assertEquals(1, receipts.size()); assertEquals(patient, receipts.get(event.eventId()).getPatientProfileId());
    }
    @ParameterizedTest @EnumSource(value = StudyVerificationStatus.class, names = {"PENDING", "REJECTED", "MISMATCH"})
    void unconfirmedOrRejectedStudyCannotBecomeReviewable(StudyVerificationStatus status) {
        request.setStatus(DiagnosticRequestStatus.IN_PROGRESS);
        var expected = new StudyEvidence(UUID.randomUUID(), study, requestId, visit, patient, attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);
        var imaging = mock(ImagingStudyPort.class);
        when(imaging.confirmIngestedStudy(any(), any(), any(), any(), any(), any(), anyList())).thenReturn(
                new StudyEvidence(expected.eventId(), study, requestId, visit, patient, attemptId, status, true));
        context.getBeanFactory().registerSingleton("externalImagingConfirmationDouble", imaging);
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> bean(DiagnosticEvidenceService.class)
                .linkConfirmedStudy(DiagnosticActor.trustedIntegration(), expected, "test"));
        assertEquals(DiagnosticRequestStatus.IN_PROGRESS, request.getStatus()); assertTrue(events.isEmpty());
    }
    @Test void wrongStudyOrStaleAttemptDenied() {
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> findings().review(actor(), requestId, "wrong", 0, "other-study", "test"));
        request.setCurrentAttemptId(UUID.randomUUID()); error(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE, this::review);
    }
    @Test void previousAttemptStudyCannotBeReviewedAgainstNewCurrentAttempt() {
        UUID current = UUID.randomUUID(); request.setCurrentAttemptId(current);
        when(bean(DiagnosticAttemptJpaRepository.class).findByRequestIdAndId(requestId, current)).thenReturn(Optional.of(
                new DiagnosticAttemptJpaEntity(current, requestId, 2, UUID.randomUUID(), "ACQUISITION_STARTED", "IMAGING", null, Instant.EPOCH, "new-study")));
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, this::review);
    }
    @Test void staleVersionRejectedAndDraftRequiresReviewedStudy() {
        error(DiagnosticOperationOutcome.VERSION_CONFLICT, () -> findings().review(actor(), requestId, "stale", 99, study, "test"));
        error(DiagnosticOperationOutcome.STATE_CONFLICT, () -> findings().saveDraft(actor(), requestId, "unreviewed", 0, "finding", "test"));
    }
    @Test void reviewedDraftCompletionNeedsValidatedReportAndMatchingStudy() {
        var draft = draft(); UUID invalid = report(draft.version(), DiagnosticReportValidationStatus.INVALID);
        error(DiagnosticOperationOutcome.REPORT_NOT_READY, () -> findings().complete(actor(), requestId, "invalid", 1, draft.version(), invalid, study, "test"));
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> findings().complete(actor(), requestId, "wrong", 1, draft.version(), invalid, "wrong", "test"));
    }
    @Test void resultReadyReceiptIsReplaySafeAndCannotMutateVisitOrExamination() {
        var event = ready(visit); var port = bean(VisitDiagnosticPort.class);
        port.publishResultReady(requestId, visit, event.getEventId()); port.publishResultReady(requestId, visit, event.getEventId());
        assertEquals(1, receipts.size()); assertEquals(patient, receipts.values().iterator().next().getPatientProfileId());
        assertEquals("OPEN", bean(ClinicVisitJpaRepository.class).findById(visit).orElseThrow().getStatus());
        verify(bean(ClinicVisitJpaRepository.class), never()).save(any());
        verifyNoInteractions(bean(fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalExaminationRepository.class));
    }
    @Test void resultReadyRejectsWrongVisitAndPatient() {
        var wrong = ready(UUID.randomUUID());
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> bean(VisitDiagnosticPort.class).publishResultReady(requestId, wrong.getVisitId(), wrong.getEventId()));
        var event = ready(visit); when(bean(ClinicVisitJpaRepository.class).findById(visit)).thenReturn(Optional.of(ClinicVisitJpaEntity.walkIn(visit, UUID.randomUUID(), null, account, Instant.EPOCH)));
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> bean(VisitDiagnosticPort.class).publishResultReady(requestId, visit, event.getEventId())); assertTrue(receipts.isEmpty());
    }
    @Test void resultReadyRequiresExistingVisit() {
        var event = ready(visit);
        when(bean(ClinicVisitJpaRepository.class).findById(visit)).thenReturn(Optional.empty());
        error(DiagnosticOperationOutcome.RECORD_NOT_FOUND, () -> bean(VisitDiagnosticPort.class).publishResultReady(requestId, visit, event.getEventId()));
        assertTrue(receipts.isEmpty());
    }
    @Test void releaseAcknowledgesExactEventAndRejectsConflictingPayload() {
        var event = release(); var port = bean(VisitDiagnosticPort.class);
        var ack = port.publishFindingsReleased(event); assertTrue(ack.accepted()); assertEquals(event.eventId(), ack.eventId());
        assertEquals(ack, port.publishFindingsReleased(event)); assertEquals(1, receipts.size());
        var conflict = new FindingsReleaseEvent(event.eventId(), requestId, visit, patient, study, event.findingsVersionId(), UUID.randomUUID(), event.reportSha256());
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> port.publishFindingsReleased(conflict));
        var wrongPatient = new FindingsReleaseEvent(event.eventId(), requestId, visit, UUID.randomUUID(), study, event.findingsVersionId(), event.reportId(), event.reportSha256());
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> port.publishFindingsReleased(wrongPatient));
        assertNull(reportRows.get(event.reportId()).getReleasedAt()); // Visit ack alone does not release to Patient.
        var wrongVisit = new FindingsReleaseEvent(event.eventId(), requestId, UUID.randomUUID(), patient, study, event.findingsVersionId(), event.reportId(), event.reportSha256());
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> port.publishFindingsReleased(wrongVisit));
    }
    @Test void receiptRejectsReusedEventWithDifferentPreviouslyAcknowledgedHash() {
        var event = release(); bean(VisitDiagnosticPort.class).publishFindingsReleased(event);
        when(receipts.get(event.eventId()).getPayloadSha256()).thenReturn("b".repeat(64));
        error(DiagnosticOperationOutcome.IDEMPOTENCY_CONFLICT, () -> bean(VisitDiagnosticPort.class).publishFindingsReleased(event));
    }
    @Test void missingImagingOwnerKeepsReleasePending() {
        var event = release();
        var result = bean(DiagnosticEvidenceService.class).dispatchDueOutbox(10, new DiagnosticEvidenceService.RetryPolicy(3, 1, 8));
        assertEquals(0, result.delivered()); assertEquals(1, result.retryScheduled()); assertNull(reportRows.get(event.reportId()).getReleasedAt());
    }
    @Test void imagingFailureAfterVisitAcknowledgementRetriesWithOneVisitReceipt() {
        var event = release(); var imaging = mock(ImagingStudyPort.class);
        when(imaging.acceptFindingsRelease(any())).thenReturn(new ReleaseAcknowledgement(event.eventId(), requestId, "wrong-hash", true));
        context.getBeanFactory().registerSingleton("externalImagingContractDouble", imaging);
        var retry = new DiagnosticEvidenceService.RetryPolicy(3, 1, 8);
        assertEquals(1, bean(DiagnosticEvidenceService.class).dispatchDueOutbox(10, retry).retryScheduled());
        assertEquals(1, receipts.size()); assertNull(reportRows.get(event.reportId()).getReleasedAt());
        doAnswer(call -> new ReleaseAcknowledgement(event.eventId(), requestId,
                bean(DiagnosticResultEventQueryService.class).requireFindingsRelease(event).payloadSha256(), true))
                .when(imaging).acceptFindingsRelease(any());
        assertEquals(1, bean(DiagnosticEvidenceService.class).dispatchDueOutbox(10, retry).delivered());
        assertEquals(1, receipts.size()); assertNotNull(reportRows.get(event.reportId()).getReleasedAt());
    }
    @Test void bothOwnerAcknowledgementsCompleteExistingOutboxFlow() {
        var event = release(); var imaging = mock(ImagingStudyPort.class);
        when(imaging.acceptFindingsRelease(any())).thenAnswer(call -> {
            var value = (FindingsReleaseEvent) call.getArgument(0);
            String hash = bean(DiagnosticResultEventQueryService.class).requireFindingsRelease(value).payloadSha256();
            return new ReleaseAcknowledgement(value.eventId(), value.requestId(), hash, true);
        });
        context.getBeanFactory().registerSingleton("externalImagingContractDouble", imaging);
        var result = bean(DiagnosticEvidenceService.class).dispatchDueOutbox(10, new DiagnosticEvidenceService.RetryPolicy(3, 1, 8));
        assertEquals(1, result.delivered()); assertNotNull(reportRows.get(event.reportId()).getReleasedAt());
        assertEquals(DiagnosticFindingsVersionState.COMPLETED, versions.get(event.findingsVersionId()).getState());
        assertEquals("RELEASED", findings().complete(actor(), requestId, "complete", 1, 1, event.reportId(), study, "test").releaseState());
    }
    @Test void uploadRetainsTheActualVerifiedPdfHash() throws Exception {
        review(); byte[] pdf = "%PDF-1.7\nfixture\n%%EOF".getBytes(StandardCharsets.UTF_8);
        var storage = mock(ReportStoragePort.class);
        when(storage.storePrivateReport(any(), any(), any(), anyInt(), any(), anyLong())).thenAnswer(call -> {
            ((InputStream) call.getArgument(4)).readAllBytes();
            return new StoredReport(UUID.randomUUID(), requestId, visit, study, 1, "private/report", ReportValidationStatus.VALIDATED);
        }); context.getBeanFactory().registerSingleton("externalReportStorageDouble", storage);
        var uploaded = findings().uploadReport(actor(), requestId, "pdf", 1,
                new DiagnosticReportUpload(() -> new ByteArrayInputStream(pdf), pdf.length, "application/pdf"), "test");
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)), reportRows.get(uploaded.reportId()).getContentSha256());
    }
    @Test void historicalDoctorNeedsDurableCareAndOtherPatientAuthorizationIsSeparate() {
        var assignment = VisitCareAssignmentJpaEntity.inProgress(UUID.randomUUID(), visit, doctor, "ORTHOPEDIC", 30, Instant.EPOCH, Instant.EPOCH.plusSeconds(1800));
        when(bean(VisitCareAssignmentRepository.class).findFirstByVisitIdAndDoctorProfileIdAndStatusInOrderByAssignedAtDescIdDesc(eq(visit), eq(doctor), any())).thenReturn(Optional.of(assignment));
        assertTrue(bean(VisitDiagnosticPort.class).authorizeImagingHistory(visit, account).authorized());
        error(DiagnosticOperationOutcome.RECORD_NOT_FOUND, () -> bean(VisitDiagnosticPort.class).authorizeImagingHistory(UUID.randomUUID(), account));
    }
    @Test void historicalDoctorWithoutExaminedCareDenied() {
        assertFalse(bean(VisitDiagnosticPort.class).authorizeImagingHistory(visit, account).authorized());
    }
    @Test void activePatientReceivesOnlyVisitLinkageWithoutDoctorEvidence() {
        UUID patientAccount = UUID.randomUUID();
        when(bean(AccountRepository.class).existsByIdAndAccountTypeAndStatus(eq(patientAccount), eq(AccountType.PATIENT), any())).thenReturn(true);
        var linkage = bean(VisitDiagnosticPort.class).authorizeImagingHistory(visit, patientAccount);
        assertTrue(linkage.authorized()); assertEquals(patient, linkage.patientId()); assertNull(linkage.examinationEvidenceId());
        verify(bean(VisitCareAssignmentRepository.class), never()).findFirstByVisitIdAndDoctorProfileIdAndStatusInOrderByAssignedAtDescIdDesc(any(), any(), any());
    }
    @Test void noShowRemainsExplicitlyUnsupportedWithoutFakeResumeFacts() {
        error(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE, () -> bean(VisitDiagnosticPort.class)
                .acceptDeferredNoShowCancellation(requestId, visit, Instant.EPOCH, UUID.randomUUID()));
        assertTrue(receipts.isEmpty());
    }
}
