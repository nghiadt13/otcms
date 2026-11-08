package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService.ClinicalCompletionEvidence;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.visit.repository.*;
import fu.fa26.sep490.g58.otcms_be.visit.service.*;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.WalkInVisitLifecycleServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VisitClosureServiceTest {
    final UUID id = UUID.randomUUID(), actor = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-05T05:00:00Z");
    final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    final ClinicVisitJpaRepository visits = mock(ClinicVisitJpaRepository.class);
    final WalkInVisitLifecycleRepository lifecycle = mock(WalkInVisitLifecycleRepository.class);
    final VisitAuditJpaRepository audits = mock(VisitAuditJpaRepository.class);
    final ExitWalkInReceptionQueueService queue = mock(ExitWalkInReceptionQueueService.class);
    final ReceptionistAccountAccessService receptionists = mock(ReceptionistAccountAccessService.class);
    final ClinicalVisitCompletionEvidenceService clinical = mock(ClinicalVisitCompletionEvidenceService.class);
    final fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService diagnostic = mock(fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService.class);
    final NotificationPublisher notifications = mock(NotificationPublisher.class);
    final VisitReviewSourceService reviews = mock(VisitReviewSourceService.class);
    final StaffAvailabilityProviderQueryService names = mock(StaffAvailabilityProviderQueryService.class);
    final WalkInVisitLifecycleServiceImpl service = new WalkInVisitLifecycleServiceImpl(guard, visits, lifecycle,
            audits, queue, receptionists, clinical, diagnostic, Clock.fixed(now, ZoneOffset.UTC), notifications, reviews, names);
    @BeforeEach void setup() {
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(true);
        when(visits.findByIdForUpdate(id)).thenReturn(Optional.of(ClinicVisitJpaEntity.walkIn(id, patient, null, actor, now)));
        when(clinical.getCompletionEvidence(id)).thenReturn(evidence(true, true, 0, true, List.of()));
        when(lifecycle.closeOpenVisit(id, now)).thenReturn(true);
        when(reviews.find(id)).thenReturn(Optional.of(new VisitReviewSourceService.Source(id, patient, "CLOSED", now, "", "", null, doctor)));
        when(names.findDoctorDisplayName(doctor)).thenReturn(Optional.of("Treating Doctor"));
    }
    ClinicalCompletionEvidence evidence(boolean exists, boolean complete, int outstanding, boolean results, List<UUID> imaging) {
        return new ClinicalCompletionEvidence(id, exists, complete, outstanding + imaging.size(), outstanding, results, imaging);
    }
    void close() { service.close(id, actor, AccountRole.RECEPTIONIST, "uc28"); }
    void denied(String code, Runnable action) { assertEquals(code, assertThrows(VisitOperationException.class, action::run).code()); }
    void unchanged() { verify(lifecycle, never()).closeOpenVisit(any(), any()); verifyNoInteractions(queue, audits, notifications); }
    @Test void activeReceptionistClosesWithCompletedClinicalEvidencePreservingSideEffectsAndLockOrder() {
        close();
        var sequence = inOrder(guard, visits, clinical, queue, lifecycle, audits, notifications);
        sequence.verify(guard).acquire(); sequence.verify(visits).findByIdForUpdate(id);
        sequence.verify(clinical).getCompletionEvidence(id);
        sequence.verify(queue).exit(argThat(c -> c.actorRole() == AccountRole.RECEPTIONIST && c.reason().equals("VISIT_CLOSED")));
        sequence.verify(lifecycle).closeOpenVisit(id, now); sequence.verify(lifecycle).releaseReservedCare(id, now);
        sequence.verify(audits).saveAndFlush(any(VisitAuditJpaEntity.class));
        sequence.verify(notifications).publishToPatient(eq(patient), argThat(m -> m.type().equals("VISIT_CLOSED_REVIEW_INVITE") && m.content().contains("Treating Doctor")));
        verify(names).findDoctorDisplayName(doctor);
    }
    @ParameterizedTest @EnumSource(value = AccountRole.class, names = {"DOCTOR", "PATIENT", "ADMIN", "OWNER", "IMAGING_TECHNICIAN"})
    void otherRolesDeniedBeforeReads(AccountRole role) {
        denied("ACCESS_DENIED", () -> service.close(id, actor, role, "uc28"));
        verifyNoInteractions(guard, visits, clinical, queue, audits, notifications);
    }
    @Test void inactiveReceptionistDenied() {
        when(receptionists.isActiveReceptionistAccount(actor)).thenReturn(false);
        denied("ACCESS_DENIED", this::close); verifyNoInteractions(guard, visits, clinical); unchanged();
    }
    @Test void noExaminationRejected() { when(clinical.getCompletionEvidence(id)).thenReturn(evidence(false, false, 0, true, List.of())); denied("VISIT_CARE_NOT_CHANGEABLE", this::close); unchanged(); }
    @Test void incompleteExaminationRejected() { when(clinical.getCompletionEvidence(id)).thenReturn(evidence(true, false, 0, true, List.of())); denied("VISIT_CARE_NOT_CHANGEABLE", this::close); unchanged(); }
    @Test void outstandingOrderRejected() { when(clinical.getCompletionEvidence(id)).thenReturn(evidence(true, true, 1, false, List.of())); denied("VISIT_CARE_NOT_CHANGEABLE", this::close); unchanged(); }
    @Test void inconsistentResultsFailClosed() { when(clinical.getCompletionEvidence(id)).thenReturn(evidence(true, true, 0, false, List.of())); denied("VISIT_CARE_NOT_CHANGEABLE", this::close); unchanged(); }
    @Test void imagingFailsClosedWhenDiagnosticOwnerCannotSupplyProof() { when(clinical.getCompletionEvidence(id)).thenReturn(evidence(true, true, 0, true, List.of(UUID.randomUUID()))); denied("VISIT_CARE_NOT_CHANGEABLE", this::close); unchanged(); }
    @Test void closedVisitRejectedBeforeEvidenceAndSideEffects() {
        var visit = mock(ClinicVisitJpaEntity.class); when(visit.getStatus()).thenReturn("CLOSED");
        when(visits.findByIdForUpdate(id)).thenReturn(Optional.of(visit));
        denied("VISIT_CARE_NOT_CHANGEABLE", this::close); verifyNoInteractions(clinical); unchanged();
    }
    @Test void inProgressCareRejected() { when(lifecycle.hasInProgressCare(id)).thenReturn(true); denied("VISIT_CARE_NOT_CHANGEABLE", this::close); verifyNoInteractions(clinical); unchanged(); }
    @Test void missingVisitRejected() { when(visits.findByIdForUpdate(id)).thenReturn(Optional.empty()); denied("RESOURCE_NOT_FOUND", this::close); unchanged(); }
    @Test void evidenceFailurePropagatesWithoutClosing() { when(clinical.getCompletionEvidence(id)).thenThrow(new IllegalStateException("owner unavailable")); assertThrows(IllegalStateException.class, this::close); unchanged(); }
    @Test void scheduledVisitClosesWithoutWalkInQueueMutation() {
        var visit = mock(ClinicVisitJpaEntity.class); when(visit.getStatus()).thenReturn("OPEN"); when(visit.getSource()).thenReturn("APPOINTMENT"); when(visit.getPatientProfileId()).thenReturn(patient);
        when(visits.findByIdForUpdate(id)).thenReturn(Optional.of(visit)); close();
        verifyNoInteractions(queue); verify(lifecycle).closeOpenVisit(id, now); verify(lifecycle).releaseReservedCare(id, now); verify(audits).saveAndFlush(any()); verify(notifications).publishToPatient(eq(patient), any());
    }
    @Test void completedReleasedImagingClosesAfterOwnerReadUnderTheGuard() {
        UUID order = UUID.randomUUID();
        when(clinical.getCompletionEvidence(id)).thenReturn(evidence(true, true, 0, true, List.of(order)));
        when(diagnostic.getCompletionEvidence(id, patient, List.of(order))).thenReturn(
                new fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService.ImagingCompletionEvidence(id, patient, Set.of(order), Set.of(order)));
        close();
        var sequence = inOrder(guard, visits, clinical, diagnostic, lifecycle);
        sequence.verify(guard).acquire(); sequence.verify(visits).findByIdForUpdate(id); sequence.verify(clinical).getCompletionEvidence(id);
        sequence.verify(diagnostic).getCompletionEvidence(id, patient, List.of(order)); sequence.verify(lifecycle).closeOpenVisit(id, now);
    }
    @Test void twoImagingOrdersRequireBothOwnerCompletions() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        when(clinical.getCompletionEvidence(id)).thenReturn(evidence(true, true, 0, true, List.of(a, b)));
        when(diagnostic.getCompletionEvidence(id, patient, List.of(a, b))).thenReturn(
                new fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService.ImagingCompletionEvidence(id, patient, Set.of(a, b), Set.of(a)));
        denied("VISIT_CARE_NOT_CHANGEABLE", this::close); unchanged();
        when(diagnostic.getCompletionEvidence(id, patient, List.of(a, b))).thenReturn(
                new fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService.ImagingCompletionEvidence(id, patient, Set.of(a, b), Set.of(a, b)));
        close(); verify(lifecycle).closeOpenVisit(id, now);
    }
    @Test void ownerEvidenceCannotSubstituteDifferentVisitPatientOrRequiredOrders() {
        UUID order = UUID.randomUUID();
        when(clinical.getCompletionEvidence(id)).thenReturn(evidence(true, true, 0, true, List.of(order)));
        for (var proof : List.of(
                new fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService.ImagingCompletionEvidence(UUID.randomUUID(), patient, Set.of(order), Set.of(order)),
                new fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService.ImagingCompletionEvidence(id, UUID.randomUUID(), Set.of(order), Set.of(order)),
                new fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService.ImagingCompletionEvidence(id, patient, Set.of(), Set.of()))) {
            when(diagnostic.getCompletionEvidence(id, patient, List.of(order))).thenReturn(proof);
            denied("VISIT_CARE_NOT_CHANGEABLE", this::close); unchanged();
        }
    }
    @Test void noRequiredImagingDoesNotReadDiagnostic() { close(); verifyNoInteractions(diagnostic); }

    @Test void cancellationRetainsWalkInContractAndDoesNotRequireClinicalCompletion() {
        when(lifecycle.closeOpenWalkIn(id, now)).thenReturn(true);
        service.cancel(id, actor, AccountRole.RECEPTIONIST, "cancel");
        verifyNoInteractions(clinical, notifications); verify(lifecycle).closeOpenWalkIn(id, now);
        verify(queue).exit(argThat(c -> c.reason().equals("CANCELLED")));
    }
}
