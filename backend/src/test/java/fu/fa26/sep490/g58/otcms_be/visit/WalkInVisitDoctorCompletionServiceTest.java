package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService.ClinicalCompletionEvidence;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitCompletionResult;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitAuditJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInVisitLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitCareAssignmentService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.WalkInVisitLifecycleServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Doctor completion (walk-in endpoint and scheduled path) in the order of plans/261111-0208-visit-flow-r3, BE-4 R4:
 * the actor and the visit kind first, then the queue entry (this doctor, under examination), then evidence, and only
 * then any write.
 */
class WalkInVisitDoctorCompletionServiceTest {
    private final UUID visitId = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final UUID doctorAccount = UUID.randomUUID();
    private final UUID doctorProfile = UUID.randomUUID();
    private final UUID otherDoctor = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-11T02:00:00Z");
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final ClinicVisitJpaRepository visits = mock(ClinicVisitJpaRepository.class);
    private final WalkInVisitLifecycleRepository lifecycle = mock(WalkInVisitLifecycleRepository.class);
    private final VisitAuditJpaRepository audits = mock(VisitAuditJpaRepository.class);
    private final ExitWalkInReceptionQueueService queue = mock(ExitWalkInReceptionQueueService.class);
    private final ReceptionistAccountAccessService receptionists = mock(ReceptionistAccountAccessService.class);
    private final ClinicalVisitCompletionEvidenceService clinical = mock(ClinicalVisitCompletionEvidenceService.class);
    private final DiagnosticVisitCompletionEvidenceService diagnostic = mock(DiagnosticVisitCompletionEvidenceService.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);
    private final VisitReviewSourceService reviews = mock(VisitReviewSourceService.class);
    private final StaffAvailabilityProviderQueryService names = mock(StaffAvailabilityProviderQueryService.class);
    private final VisitExamQueueService examQueue = mock(VisitExamQueueService.class);
    private final GetClinicServiceCatalogService catalog = mock(GetClinicServiceCatalogService.class);
    private final ManageVisitCareAssignmentService careAssignments = mock(ManageVisitCareAssignmentService.class);
    private final DoctorProfileIdentityQueryService doctorProfiles = mock(DoctorProfileIdentityQueryService.class);
    private final WalkInVisitLifecycleServiceImpl service = new WalkInVisitLifecycleServiceImpl(guard, visits, lifecycle,
            audits, queue, receptionists, clinical, diagnostic, Clock.fixed(now, ZoneOffset.UTC), notifications, reviews,
            names, examQueue, catalog, careAssignments, doctorProfiles);

    @BeforeEach
    void setUp() {
        when(doctorProfiles.findDoctorProfileId(doctorAccount)).thenReturn(Optional.of(doctorProfile));
        when(visits.findByIdForUpdate(visitId)).thenReturn(Optional.of(
                ClinicVisitJpaEntity.walkIn(visitId, patient, null, doctorAccount, now)));
        when(examQueue.find(visitId)).thenReturn(Optional.of(entry(doctorProfile, VisitExamStatus.IN_EXAMINATION)));
        when(clinical.getCompletionEvidence(visitId)).thenReturn(evidence(true));
        when(lifecycle.closeOpenVisit(visitId, now)).thenReturn(true);
    }

    @Test
    void receptionistCannotUseTheDoctorEndpointAndNothingIsRead() {
        var failure = assertThrows(VisitOperationException.class, () ->
                service.completeWalkIn(visitId, doctorAccount, AccountRole.RECEPTIONIST, "corr-complete"));

        assertEquals("ACCESS_DENIED", failure.code());
        verifyNoInteractions(guard, visits, examQueue, careAssignments);
    }

    @Test
    void scheduledVisitIsNotEndedThroughTheWalkInEndpoint() {
        var scheduled = mock(ClinicVisitJpaEntity.class);
        when(scheduled.getSource()).thenReturn("SCHEDULED");
        when(scheduled.getStatus()).thenReturn("OPEN");
        when(visits.findByIdForUpdate(visitId)).thenReturn(Optional.of(scheduled));

        var failure = assertThrows(VisitOperationException.class, () ->
                service.completeWalkIn(visitId, doctorAccount, AccountRole.DOCTOR, "corr-complete"));

        assertEquals("VISIT_EXAM_STATE_CONFLICT", failure.code());
        verify(careAssignments, never()).completeDoctorCare(any(), any(), any(), any(), any());
        verify(lifecycle, never()).closeOpenVisit(any(), any());
    }

    @Test
    void examinationOfAnotherDoctorIsRefusedBeforeEvidenceIsRead() {
        when(examQueue.find(visitId)).thenReturn(Optional.of(entry(otherDoctor, VisitExamStatus.IN_EXAMINATION)));

        var failure = assertThrows(VisitOperationException.class, () ->
                service.completeWalkIn(visitId, doctorAccount, AccountRole.DOCTOR, "corr-complete"));

        assertEquals("VISIT_EXAM_STATE_CONFLICT", failure.code());
        verify(clinical, never()).getCompletionEvidence(any());
        verify(careAssignments, never()).completeDoctorCare(any(), any(), any(), any(), any());
    }

    @Test
    void queueEntryStillWaitingIsAStateConflictNotAnImplicitStart() {
        when(examQueue.find(visitId)).thenReturn(Optional.of(entry(doctorProfile, VisitExamStatus.WAITING)));

        var failure = assertThrows(VisitOperationException.class, () ->
                service.completeWalkIn(visitId, doctorAccount, AccountRole.DOCTOR, "corr-complete"));

        assertEquals("VISIT_EXAM_STATE_CONFLICT", failure.code());
        verify(clinical, never()).getCompletionEvidence(any());
        verifyNoInteractions(queue);
    }

    @Test
    void missingEvidenceStopsCompletionBeforeAnyWrite() {
        when(clinical.getCompletionEvidence(visitId)).thenReturn(evidence(false));

        var failure = assertThrows(VisitOperationException.class, () ->
                service.completeWalkIn(visitId, doctorAccount, AccountRole.DOCTOR, "corr-complete"));

        assertEquals("VISIT_COMPLETION_NOT_ALLOWED", failure.code());
        verify(careAssignments, never()).completeDoctorCare(any(), any(), any(), any(), any());
        verify(lifecycle, never()).closeOpenVisit(any(), any());
        verify(examQueue, never()).complete(any(), any(), any());
        verifyNoInteractions(notifications);
    }

    @Test
    void completionEndsCareThenVisitThenQueueAndInvitesReviewWithoutReceptionExit() {
        var result = service.completeWalkIn(visitId, doctorAccount, AccountRole.DOCTOR, "corr-complete");

        assertEquals(new VisitCompletionResult(visitId, VisitExamStatus.COMPLETED, now), result);
        var order = inOrder(careAssignments, lifecycle, examQueue, notifications);
        order.verify(careAssignments).completeDoctorCare(visitId, doctorProfile, doctorAccount, "corr-complete", now);
        order.verify(lifecycle).closeOpenVisit(visitId, now);
        order.verify(examQueue).complete(visitId, doctorProfile, now);
        order.verify(notifications).publishToPatient(eq(patient), any());
        verifyNoInteractions(queue);
    }

    private ExamQueueEntry entry(UUID responsibleDoctor, VisitExamStatus status) {
        return new ExamQueueEntry(visitId, responsibleDoctor, null, null, 1, status, LocalDate.of(2026, 11, 11),
                now, now, null, null);
    }

    private ClinicalCompletionEvidence evidence(boolean examinationCompleted) {
        return new ClinicalCompletionEvidence(visitId, true, examinationCompleted, 0, 0, true, List.of());
    }
}
