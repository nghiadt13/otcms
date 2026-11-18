package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.*;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.visit.repository.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/** Real Visit/Diagnostic/Clinical composition, doubled persistence and external Imaging contracts.
 * Does not claim PostgreSQL, Orthanc, secure viewer runtime, or queue-return execution. */
class DoctorImagingReviewIntegrationTest {
    AnnotationConfigWebApplicationContext context;
    final UUID visit = UUID.randomUUID(), patient = UUID.randomUUID(), account = UUID.randomUUID(), doctor = UUID.randomUUID();
    final UUID requestId = UUID.randomUUID(), attemptId = UUID.randomUUID();
    final String study = "confirmed-study";
    final Map<UUID, DiagnosticReportJpaEntity> reportRows = new HashMap<>();
    final AtomicReference<Instant> resultReadyAt = new AtomicReference<>();
    final AtomicReference<Instant> findingsReleasedAt = new AtomicReference<>();
    final AtomicReference<ClinicalServiceOrderExecutionPort.ExecutionStatus> executionStatus =
            new AtomicReference<>(ClinicalServiceOrderExecutionPort.ExecutionStatus.RESULT_AVAILABLE);
    final AtomicLong executionVersion = new AtomicLong();
    final AtomicReference<String> reviewKey = new AtomicReference<>();
    final AtomicReference<String> reviewFingerprint = new AtomicReference<>();
    final AtomicReference<String> executionStudy = new AtomicReference<>("confirmed-study");
    final AtomicReference<UUID> executionAttemptId = new AtomicReference<>();
    ClinicalServiceOrderExecutionPort.ImagingExecution execution;
    DiagnosticReportJpaEntity currentFindings;
    <T> T bean(Class<T> type) { return context.getBean(type); }
    DiagnosticActor actor() { return DiagnosticActor.user(account, AccountRole.DOCTOR); }
    DiagnosticFindingsService findings() { return bean(DiagnosticFindingsService.class); }
    void resultReady(UUID request, UUID visitId, UUID event) {
        new org.springframework.transaction.support.TransactionTemplate(bean(DoctorCoreProductionWiringTest.RecordingTransactions.class))
                .executeWithoutResult(status -> bean(VisitDiagnosticPort.class).publishResultReady(request, visitId, event));
    }
    ReleaseAcknowledgement releaseVisit(FindingsReleaseEvent event) {
        return new org.springframework.transaction.support.TransactionTemplate(bean(DoctorCoreProductionWiringTest.RecordingTransactions.class))
                .execute(status -> bean(VisitDiagnosticPort.class).publishFindingsReleased(event));
    }

    @BeforeEach void start() throws Exception {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(DoctorCoreProductionWiringTest.ProductionConfiguration.class, TestOwnerPorts.class); context.refresh();
        var profile = mock(StaffProfileJpaEntity.class); when(profile.id()).thenReturn(doctor);
        when(bean(StaffProfileRepository.class).findByAccountIdAndDoctorTrue(account)).thenReturn(Optional.of(profile));
        when(bean(AccountRepository.class).existsByIdAndAccountTypeAndStatus(eq(account), eq(AccountType.DOCTOR), any())).thenReturn(true);
        when(bean(ClinicVisitJpaRepository.class).findById(visit)).thenReturn(Optional.of(
                ClinicVisitJpaEntity.walkIn(visit, patient, null, account, Instant.EPOCH, null, null)));
        when(bean(ClinicVisitJpaRepository.class).markImagingResultReady(eq(visit), eq(patient), any())).thenAnswer(call -> {
            resultReadyAt.compareAndSet(null, call.getArgument(2)); return 1;
        });
        when(bean(ClinicVisitJpaRepository.class).markFindingsReleased(eq(visit), eq(patient), any())).thenAnswer(call -> {
            findingsReleasedAt.compareAndSet(null, call.getArgument(2)); return 1;
        });
        when(bean(VisitCareAssignmentRepository.class).findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visit, "IN_PROGRESS"))
                .thenReturn(Optional.of(VisitCareAssignmentJpaEntity.inProgress(UUID.randomUUID(), visit, doctor,
                        "ORTHOPEDIC", 30, Instant.EPOCH, Instant.EPOCH.plusSeconds(1800))));
        execution = mock(ClinicalServiceOrderExecutionPort.ImagingExecution.class);
        when(execution.orderId()).thenReturn(requestId); when(execution.visitId()).thenReturn(visit);
        when(execution.patientId()).thenReturn(patient); when(execution.orderingDoctorAccountId()).thenReturn(account);
        when(execution.serviceId()).thenReturn(UUID.randomUUID()); when(execution.roomId()).thenReturn(UUID.randomUUID());
        when(execution.imagingType()).thenReturn("MRI"); when(execution.bodyArea()).thenReturn("Chest");
        when(execution.clinicalReason()).thenReturn("Review findings"); executionAttemptId.set(attemptId);
        when(execution.attemptId()).thenAnswer(call -> executionAttemptId.get());
        when(execution.technicianAccountId()).thenReturn(UUID.randomUUID()); when(execution.firstUseEventId()).thenReturn(UUID.randomUUID());
        when(execution.firstUseAt()).thenReturn(Instant.EPOCH); when(execution.studyRef()).thenAnswer(call -> executionStudy.get());
        when(execution.status()).thenAnswer(call -> executionStatus.get());
        when(execution.executionVersion()).thenAnswer(call -> executionVersion.get());
        when(execution.reviewIdempotencyKey()).thenAnswer(call -> reviewKey.get());
        when(execution.reviewFingerprint()).thenAnswer(call -> reviewFingerprint.get());
        when(execution.createdAt()).thenReturn(Instant.EPOCH); when(execution.updatedAt()).thenReturn(Instant.EPOCH);
        when(bean(ClinicalServiceOrderExecutionPort.class).find(requestId)).thenAnswer(call -> Optional.of(execution));
        when(bean(ClinicalServiceOrderExecutionPort.class).findForUpdate(requestId)).thenAnswer(call -> Optional.of(execution));
        when(bean(ClinicalServiceOrderExecutionPort.class).markReviewed(eq(requestId), anyLong(), anyString(), anyString()))
                .thenAnswer(call -> {
                    if (executionVersion.get() != call.<Long>getArgument(1)
                            || executionStatus.get() != ClinicalServiceOrderExecutionPort.ExecutionStatus.RESULT_AVAILABLE) return 0;
                    executionStatus.set(ClinicalServiceOrderExecutionPort.ExecutionStatus.REVIEWED);
                    executionVersion.incrementAndGet(); reviewKey.set(call.getArgument(2)); reviewFingerprint.set(call.getArgument(3));
                    return 1;
                });
        when(bean(ClinicalServiceOrderExecutionPort.class).linkStudy(eq(requestId), anyLong(), any(), anyString()))
                .thenAnswer(call -> {
                    if (executionVersion.get() != call.<Long>getArgument(1)
                            || executionStatus.get() != ClinicalServiceOrderExecutionPort.ExecutionStatus.IN_PROGRESS) return 0;
                    executionStatus.set(ClinicalServiceOrderExecutionPort.ExecutionStatus.RESULT_AVAILABLE);
                    executionStudy.set(call.getArgument(3)); executionVersion.incrementAndGet(); return 1;
                });
        var reports = bean(DiagnosticReportJpaRepository.class);
        when(reports.saveAndFlush(any())).thenAnswer(call -> { var row = (DiagnosticReportJpaEntity) call.getArgument(0); reportRows.put(row.getId(), row); currentFindings = row; return row; });
        when(reports.findByIdAndRequestId(any(), eq(requestId))).thenAnswer(call -> Optional.ofNullable(reportRows.get(call.getArgument(0))));
        when(reports.findByRequestIdAndReportVersion(eq(requestId), anyInt())).thenAnswer(call -> reportRows.values().stream()
                .filter(row -> requestId.equals(row.getRequestId()) && row.getReportVersion() == call.<Integer>getArgument(1)).findFirst());
        when(reports.findFirstByRequestIdOrderByReportVersionDesc(requestId)).thenAnswer(call -> reportRows.values().stream()
                .filter(row -> requestId.equals(row.getRequestId())).max(Comparator.comparingInt(DiagnosticReportJpaEntity::getReportVersion)));
        when(reports.findByRequestIdAndDraftIdempotencyKey(eq(requestId), anyString())).thenAnswer(call -> reportRows.values().stream()
                .filter(row -> requestId.equals(row.getRequestId()) && call.getArgument(1).equals(row.getDraftIdempotencyKey())).findFirst());
        when(reports.findByRequestIdAndCorrectionIdempotencyKey(eq(requestId), anyString())).thenAnswer(call -> reportRows.values().stream()
                .filter(row -> requestId.equals(row.getRequestId()) && call.getArgument(1).equals(row.getCorrectionIdempotencyKey())).findFirst());
        when(reports.findByRequestIdAndUploadIdempotencyKey(eq(requestId), anyString())).thenAnswer(call -> reportRows.values().stream()
                .filter(row -> requestId.equals(row.getRequestId()) && call.getArgument(1).equals(row.getUploadIdempotencyKey())).findFirst());
        when(reports.findByRequestIdAndCompleteIdempotencyKey(eq(requestId), anyString())).thenAnswer(call -> reportRows.values().stream()
                .filter(row -> requestId.equals(row.getRequestId()) && call.getArgument(1).equals(row.getCompleteIdempotencyKey())).findFirst());
        when(reports.findByReportIdAndRequestId(any(), eq(requestId))).thenAnswer(call -> reportRows.values().stream()
                .filter(row -> requestId.equals(row.getRequestId()) && Objects.equals(call.getArgument(0), row.getReportId())).findFirst());
        var imaging = bean(ImagingStudyPort.class);
        when(imaging.acceptFindingsRelease(any())).thenAnswer(call -> {
            var event = (FindingsReleaseEvent) call.getArgument(0);
            assertNotNull(findingsReleasedAt.get());
            String hash = bean(DiagnosticResultEventQueryService.class).requireFindingsRelease(event).payloadSha256();
            return new ReleaseAcknowledgement(event.eventId(), event.requestId(), hash, true);
        });
    }
    @AfterEach void close() { context.close(); }
    void error(DiagnosticOperationOutcome expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(DiagnosticOperationException.class, action).outcome());
    }
    void review() { findings().review(actor(), requestId, "review", 0, study, "test"); }
    DiagnosticFindingsView draft() { if (executionStatus.get() != ClinicalServiceOrderExecutionPort.ExecutionStatus.REVIEWED) review(); return findings().saveDraft(actor(), requestId, "draft", executionVersion.get(), "No fracture", "test"); }
    UUID report(int version, DiagnosticReportValidationStatus validation) {
        DiagnosticReportJpaEntity row = reportRows.values().stream()
                .filter(value -> requestId.equals(value.getRequestId()) && value.getReportVersion() == version).findFirst().orElseThrow();
        UUID id = UUID.randomUUID();
        row.attachReport(id, "private/report", "a".repeat(64), validation, account, Instant.EPOCH, Instant.EPOCH);
        return id;
    }
    FindingsReleaseEvent release() {
        var draft = draft(); var reportId = report(draft.version(), DiagnosticReportValidationStatus.VALIDATED);
        findings().complete(actor(), requestId, "complete", executionVersion.get(), draft.version(), reportId, study, "test");
        UUID eventId = UUID.nameUUIDFromBytes(("FINDINGS_RELEASE:" + requestId + ":complete").getBytes(StandardCharsets.UTF_8));
        return new FindingsReleaseEvent(eventId, requestId, visit, patient, study, draft.findingsVersionId(), reportId, "a".repeat(64));
    }
    @Test void correctionAndCompletionReplayUseCompletedManagedPayloadAndPreserveReleaseGates() {
        var initial = draft();
        UUID firstReport = report(1, DiagnosticReportValidationStatus.VALIDATED);
        var old = reportRows.get(initial.findingsVersionId());
        findings().complete(actor(), requestId, "complete-initial", executionVersion.get(), initial.version(),
                firstReport, study, "test");
        long requestVersion = executionVersion.get();
        var correction = findings().createCorrection(actor(), requestId, "correction", requestVersion,
                "Small effusion", "Image re-review", "test");
        var replay = findings().createCorrection(actor(), requestId, "correction", requestVersion,
                "Small effusion", "Image re-review", "replay");
        assertEquals(correction.findingsVersionId(), replay.findingsVersionId());
        assertEquals(2, replay.version()); assertEquals("PRIVATE", replay.releaseState());
        assertEquals(2, reportRows.size());
        UUID secondReport = report(2, DiagnosticReportValidationStatus.VALIDATED);
        findings().complete(actor(), requestId, "complete-correction", requestVersion, 2, secondReport, study, "test");
        assertEquals("RELEASED", findings().complete(actor(), requestId, "complete-correction",
                requestVersion, 2, secondReport, study, "replay").releaseState());
        // Both owner acknowledgements are performed synchronously in complete().
        assertNotNull(reportRows.values().stream().filter(row -> secondReport.equals(row.getReportId()))
                .findFirst().orElseThrow().getReleasedAt());
        assertEquals(DiagnosticFindingsVersionState.COMPLETED, reportRows.get(correction.findingsVersionId()).getState());
        assertNotNull(reportRows.values().stream().filter(row -> secondReport.equals(row.getReportId()))
                .findFirst().orElseThrow().getReleasedAt());
        assertEquals("RELEASED", findings().complete(actor(), requestId, "complete-correction",
                requestVersion, 2, secondReport, study, "released-replay").releaseState());
        verify(bean(ImagingStudyPort.class), atLeastOnce()).acceptFindingsRelease(any());
        assertEquals("No fracture", old.getFindingsText());
        assertEquals(firstReport, old.getReportId()); assertNotNull(old.getCompletedAt());
        assertThrows(IllegalStateException.class, () -> old.reviseDraft("changed", Instant.now()));
        verify(bean(DiagnosticReportJpaRepository.class), atLeastOnce()).saveAndFlush(any());
    }

    @Test void currentDoctorReviewsAndIdenticalReplayIsSafe() {
        review(); assertEquals(ClinicalServiceOrderExecutionPort.ExecutionStatus.REVIEWED, executionStatus.get());
        findings().review(actor(), requestId, "review", 0, study, "test"); assertEquals(1, executionVersion.get());
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
    /** UC-50 (R3): the requesting doctor keeps access when the visit moved to another doctor. */
    @Test void formerRequestingDoctorKeepsReviewAccessWhenCurrentDoctorChanges() {
        when(bean(VisitCareAssignmentRepository.class).findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visit, "IN_PROGRESS"))
                .thenReturn(Optional.of(VisitCareAssignmentJpaEntity.inProgress(UUID.randomUUID(), visit, UUID.randomUUID(), "ORTHOPEDIC", 30, Instant.EPOCH, Instant.EPOCH.plusSeconds(1800))));
        review();
        assertEquals(ClinicalServiceOrderExecutionPort.ExecutionStatus.REVIEWED, executionStatus.get());
    }
    @Test void mismatchedPatientDeniedBeforeReview() {
        when(bean(ClinicVisitJpaRepository.class).findById(visit)).thenReturn(Optional.of(ClinicVisitJpaEntity.walkIn(visit, UUID.randomUUID(), null, account, Instant.EPOCH, null, null)));
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, this::review);
    }
    @Test void closedVisitCannotReview() {
        var closed = mock(ClinicVisitJpaEntity.class); when(closed.getId()).thenReturn(visit); when(closed.getPatientProfileId()).thenReturn(patient); when(closed.getStatus()).thenReturn("CLOSED");
        when(bean(ClinicVisitJpaRepository.class).findById(visit)).thenReturn(Optional.of(closed));
        error(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE, this::review);
    }
    @ParameterizedTest @EnumSource(value = DiagnosticRequestStatus.class, names = {"PENDING", "IN_PROGRESS", "CANCELLED"})
    void nonReviewableStatesDenied(DiagnosticRequestStatus status) {
        executionStatus.set(ClinicalServiceOrderExecutionPort.ExecutionStatus.valueOf(status.name()));
        executionStudy.set(null);
        executionAttemptId.set(status == DiagnosticRequestStatus.IN_PROGRESS ? attemptId : null);
        error(DiagnosticOperationOutcome.STATE_CONFLICT, this::review);
    }
    @Test void matchedConfirmedStudyCoordinatesRealVisitSynchronously() {
        executionStatus.set(ClinicalServiceOrderExecutionPort.ExecutionStatus.IN_PROGRESS);
        executionStudy.set(null);
        var event = new StudyEvidence(UUID.randomUUID(), study, requestId, visit, patient, attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);
        when(bean(ImagingStudyPort.class).confirmIngestedStudy(event.eventId(), study, requestId, visit, patient,
                attemptId, event.verificationEvidence())).thenReturn(event);
        var result = bean(DiagnosticEvidenceService.class).linkConfirmedStudy(DiagnosticActor.trustedIntegration(), event, "test");
        assertEquals(DiagnosticRequestStatus.RESULT_AVAILABLE, result.status());
        verify(bean(ImagingStudyPort.class)).confirmIngestedStudy(event.eventId(), study, requestId, visit, patient, attemptId, event.verificationEvidence());
        verify(bean(ClinicalImagingOrderPort.class)).markResultAvailable(requestId);
        assertNotNull(resultReadyAt.get());
        verify(bean(ClinicVisitJpaRepository.class)).markImagingResultReady(eq(visit), eq(patient), any());
    }
    @ParameterizedTest @EnumSource(value = StudyVerificationStatus.class, names = {"PENDING", "REJECTED", "MISMATCH"})
    void unconfirmedOrRejectedStudyCannotBecomeReviewable(StudyVerificationStatus status) {
        executionStatus.set(ClinicalServiceOrderExecutionPort.ExecutionStatus.IN_PROGRESS);
        executionStudy.set(null);
        var expected = new StudyEvidence(UUID.randomUUID(), study, requestId, visit, patient, attemptId, StudyVerificationStatus.CONFIRMED_MATCH, true);
        var imaging = mock(ImagingStudyPort.class);
        when(imaging.confirmIngestedStudy(any(), any(), any(), any(), any(), any(), anyList())).thenReturn(
                new StudyEvidence(expected.eventId(), study, requestId, visit, patient, attemptId, status, true));
        when(bean(ImagingStudyPort.class).confirmIngestedStudy(any(), any(), any(), any(), any(), any(), anyList()))
                .thenAnswer(call -> new StudyEvidence(call.getArgument(0), study, requestId, visit, patient, attemptId, status, true));
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> bean(DiagnosticEvidenceService.class)
                .linkConfirmedStudy(DiagnosticActor.trustedIntegration(), expected, "test"));
        assertEquals(ClinicalServiceOrderExecutionPort.ExecutionStatus.IN_PROGRESS, executionStatus.get());
        assertNull(resultReadyAt.get());
    }
    @Test void wrongStudyOrMissingCurrentAttemptDenied() {
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> findings().review(actor(), requestId, "wrong", 0, "other-study", "test"));
        executionAttemptId.set(null); error(DiagnosticOperationOutcome.STATE_CONFLICT, this::review);
    }
    @Test void previousAttemptStudyCannotBeReviewedAgainstNewCurrentAttempt() {
        executionAttemptId.set(UUID.randomUUID()); executionStudy.set("new-study");
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
    @Test void resultReadyTimestampIsReplaySafeAndCannotMutateVisitOrExamination() {
        UUID event = UUID.randomUUID();
        resultReady(requestId, visit, event);
        Instant first = resultReadyAt.get();
        resultReady(requestId, visit, event);
        assertNotNull(first); assertEquals(first, resultReadyAt.get());
        assertEquals("OPEN", bean(ClinicVisitJpaRepository.class).findById(visit).orElseThrow().getStatus());
        verify(bean(ClinicVisitJpaRepository.class), never()).save(any());
        verifyNoInteractions(bean(fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalExaminationRepository.class));
    }
    @Test void resultReadyRejectsWrongVisitAndPatient() {
        UUID wrongVisit = UUID.randomUUID();
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> resultReady(requestId, wrongVisit, UUID.randomUUID()));
        when(bean(ClinicVisitJpaRepository.class).markImagingResultReady(eq(visit), eq(patient), any())).thenReturn(0);
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> resultReady(requestId, visit, UUID.randomUUID())); assertNull(resultReadyAt.get());
    }
    @Test void resultReadyRequiresExistingVisit() {
        when(bean(ClinicVisitJpaRepository.class).markImagingResultReady(eq(visit), eq(patient), any())).thenReturn(0);
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> resultReady(requestId, visit, UUID.randomUUID()));
        assertNull(resultReadyAt.get());
    }
    @Test void releaseAcknowledgesExactEventAndRejectsConflictingPayload() {
        var event = release();
        var ack = releaseVisit(event); assertTrue(ack.accepted()); assertEquals(event.eventId(), ack.eventId());
        Instant firstRelease = findingsReleasedAt.get();
        assertEquals(ack, releaseVisit(event)); assertEquals(firstRelease, findingsReleasedAt.get());
        var conflict = new FindingsReleaseEvent(event.eventId(), requestId, visit, patient, study, event.findingsVersionId(), UUID.randomUUID(), event.reportSha256());
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> releaseVisit(conflict));
        var wrongPatient = new FindingsReleaseEvent(event.eventId(), requestId, visit, UUID.randomUUID(), study, event.findingsVersionId(), event.reportId(), event.reportSha256());
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> releaseVisit(wrongPatient));
        assertNotNull(reportRows.get(event.findingsVersionId()).getReleasedAt()); // The synchronous findings service owns release state.
        var wrongVisit = new FindingsReleaseEvent(event.eventId(), requestId, UUID.randomUUID(), patient, study, event.findingsVersionId(), event.reportId(), event.reportSha256());
        error(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, () -> releaseVisit(wrongVisit));
    }
    @Test void confirmationEventRejectsDifferentStudyAndVerificationEvidence() {
        var studies = mock(fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository.class);
        var row = mock(fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity.class);
        UUID eventId = UUID.randomUUID();
        when(studies.findByConfirmationEventId(eventId)).thenReturn(Optional.of(row));
        when(row.getStudyRef()).thenReturn(study);
        when(row.getRequestId()).thenReturn(requestId);
        when(row.getVisitId()).thenReturn(visit);
        when(row.getPatientId()).thenReturn(patient);
        when(row.getAttemptId()).thenReturn(attemptId);
        when(row.getVerificationEvidence()).thenReturn(List.of("MATCH"));
        when(row.getVerificationStatus()).thenReturn("CONFIRMED_MATCH");
        when(row.getState()).thenReturn(fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState.LINKED);
        var adapter = new fu.fa26.sep490.g58.otcms_be.imaging.integration.diagnostic.ImagingStudyContractAdapter(
                studies, mock(fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService.class),
                mock(DiagnosticViewerAuthorizationService.class),
                mock(fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService.class));
        assertNotNull(adapter.confirmIngestedStudy(eventId, study, requestId, visit, patient, attemptId, List.of("MATCH")));
        for (var conflicting : List.of("different-study", study)) {
            var evidence = conflicting.equals(study) ? List.of("DIFFERENT") : List.of("MATCH");
            var ex = assertThrows(fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException.class,
                    () -> adapter.confirmIngestedStudy(eventId, conflicting, requestId, visit, patient, attemptId, evidence));
            assertEquals(fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome.EVIDENCE_MISMATCH, ex.outcome());
        }
    }
    @Test void imagingOwnerFailureAbortsSynchronousReleaseCall() {
        var draft = draft(); UUID reportId = report(draft.version(), DiagnosticReportValidationStatus.VALIDATED);
        doThrow(new IllegalStateException("imaging owner unavailable"))
                .when(bean(ImagingStudyPort.class)).acceptFindingsRelease(any());
        assertThrows(DiagnosticOperationException.class, () -> findings().complete(actor(), requestId, "complete",
                executionVersion.get(), draft.version(), reportId, study, "test"));
    }
    @Test void invalidImagingAcknowledgementAbortsSynchronousRelease() {
        var draft = draft(); UUID reportId = report(draft.version(), DiagnosticReportValidationStatus.VALIDATED);
        doAnswer(call -> new ReleaseAcknowledgement(UUID.randomUUID(), requestId, "wrong-hash", true))
                .when(bean(ImagingStudyPort.class)).acceptFindingsRelease(any());
        assertThrows(DiagnosticOperationException.class, () -> findings().complete(actor(), requestId, "complete",
                executionVersion.get(), draft.version(), reportId, study, "test"));
    }
    @Test void bothOwnerAcknowledgementsCompleteFindingsSynchronously() {
        var event = release(); var imaging = mock(ImagingStudyPort.class);
        assertNotNull(reportRows.get(event.findingsVersionId()).getReleasedAt());
        assertEquals(DiagnosticFindingsVersionState.COMPLETED, reportRows.get(event.findingsVersionId()).getState());
        verify(bean(ImagingStudyPort.class)).acceptFindingsRelease(any());
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
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)), reportRows.values().stream()
                .filter(row -> uploaded.reportId().equals(row.getReportId())).findFirst().orElseThrow().getContentSha256());
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
        assertNull(resultReadyAt.get()); assertNull(findingsReleasedAt.get());
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestOwnerPorts {
        @org.springframework.context.annotation.Bean
        ImagingStudyPort testImagingStudyPort() { return mock(ImagingStudyPort.class); }

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        ClinicalImagingOrderPort testClinicalImagingOrderPort() { return mock(ClinicalImagingOrderPort.class); }
    }
}
