package fu.fa26.sep490.g58.otcms_be.visit.service.impl;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitAuditJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInVisitLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInVisitLifecycleService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalkInVisitLifecycleServiceImpl implements WalkInVisitLifecycleService {
    private final CoordinationGuardService guard;
    private final ClinicVisitJpaRepository visits;
    private final WalkInVisitLifecycleRepository lifecycle;
    private final VisitAuditJpaRepository audits;
    private final ExitWalkInReceptionQueueService queueExit;
    private final ReceptionistAccountAccessService receptionists;
    private final ClinicalVisitCompletionEvidenceService clinical;
    private final DiagnosticVisitCompletionEvidenceService diagnostic;
    private final Clock clock;
    private final NotificationPublisher notifications;
    private final VisitReviewSourceService reviewSources;
    private final StaffAvailabilityProviderQueryService doctorNames;

    public WalkInVisitLifecycleServiceImpl(CoordinationGuardService guard, ClinicVisitJpaRepository visits,
            WalkInVisitLifecycleRepository lifecycle, VisitAuditJpaRepository audits,
            ExitWalkInReceptionQueueService queueExit, ReceptionistAccountAccessService receptionists,
            ClinicalVisitCompletionEvidenceService clinical, DiagnosticVisitCompletionEvidenceService diagnostic, Clock clock,
            NotificationPublisher notifications, VisitReviewSourceService reviewSources,
            StaffAvailabilityProviderQueryService doctorNames) {
        this.guard = guard;
        this.visits = visits;
        this.lifecycle = lifecycle;
        this.audits = audits;
        this.queueExit = queueExit;
        this.receptionists = receptionists;
        this.clinical = clinical;
        this.diagnostic = diagnostic;
        this.clock = clock;
        this.notifications = notifications;
        this.reviewSources = reviewSources;
        this.doctorNames = doctorNames;
    }

    @Override
    @Transactional
    public void cancel(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId) {
        validate(visitId, actorAccountId, actorRole, correlationId);
        if (actorRole != AccountRole.RECEPTIONIST || !receptionists.isActiveReceptionistAccount(actorAccountId)) {
            throw new VisitOperationException("ACCESS_DENIED");
        }
        guard.acquire();
        var visit = visits.findByIdForUpdate(visitId)
                .orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        if (!"WALK_IN".equals(visit.getSource()) || !"OPEN".equals(visit.getStatus())) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        if (lifecycle.hasCareStarted(visitId)) {
            throw new VisitOperationException("VISIT_CANCELLATION_NOT_ALLOWED");
        }
        closeAndRecord(visitId, actorAccountId, actorRole, correlationId, "CANCELLED", clock.instant(), true);
    }

    @Override
    @Transactional
    public void close(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId) {
        validate(visitId, actorAccountId, actorRole, correlationId);
        if (actorRole != AccountRole.RECEPTIONIST || !receptionists.isActiveReceptionistAccount(actorAccountId)) {
            throw new VisitOperationException("ACCESS_DENIED");
        }
        guard.acquire();
        var visit = visits.findByIdForUpdate(visitId)
                .orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        if (!"OPEN".equals(visit.getStatus()) || lifecycle.hasInProgressCare(visitId)) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        // Guard and Visit row lock remain held through the owner read and commit.
        var evidence = clinical.getCompletionEvidence(visitId);
        if (evidence == null || !visitId.equals(evidence.visitId()) || !evidence.examinationExists()
                || !evidence.examinationCompleted() || evidence.outstandingOrderCount() != 0
                || !evidence.allNonCancelledOrdersHaveResults()) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        if (!evidence.imagingOrderIds().isEmpty()) {
            var imaging = diagnostic.getCompletionEvidence(visitId, visit.getPatientProfileId(), evidence.imagingOrderIds());
            if (imaging == null || !visitId.equals(imaging.visitId())
                    || !visit.getPatientProfileId().equals(imaging.patientId())
                    || !java.util.Set.copyOf(evidence.imagingOrderIds()).equals(imaging.requiredServiceOrderIds())
                    || !imaging.allComplete()) {
                throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
            }
        }
        var closedAt = clock.instant();
        closeAndRecord(visitId, actorAccountId, actorRole, correlationId, "VISIT_CLOSED", closedAt, "WALK_IN".equals(visit.getSource()));
        UUID treatingDoctor = reviewSources.find(visitId).map(VisitReviewSourceService.Source::doctorProfileId)
                .orElse(null);
        String doctorName = treatingDoctor == null ? "" : doctorNames.findDoctorDisplayName(treatingDoctor).orElse("");
        notifications.publishToPatient(visit.getPatientProfileId(),
                NotificationMessages.visitClosedReviewInvite(visitId, closedAt, doctorName));
    }

    private void closeAndRecord(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId,
            String reason, java.time.Instant now, boolean walkIn) {
        if (walkIn) {
            queueExit.exit(new ExitWalkInReceptionQueueCommand(visitId, actorAccountId, actorRole,
                    reason, now, correlationId));
        }
        boolean closed = "CANCELLED".equals(reason)
                ? lifecycle.closeOpenWalkIn(visitId, now) : lifecycle.closeOpenVisit(visitId, now);
        if (!closed) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        lifecycle.releaseReservedCare(visitId, now);
        VisitAuditJpaEntity audit = "CANCELLED".equals(reason)
                ? VisitAuditJpaEntity.walkInCancelled(actorAccountId, visitId, now, correlationId)
                : walkIn ? VisitAuditJpaEntity.walkInClosed(actorAccountId, visitId, now, correlationId)
                        : VisitAuditJpaEntity.visitClosed(actorAccountId, visitId, now, correlationId);
        audits.saveAndFlush(audit);
    }

    private static void validate(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId) {
        if (visitId == null || actorAccountId == null || actorRole == null || correlationId == null
                || correlationId.isBlank() || correlationId.length() > 128) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
    }
}
