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
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitCompletionResult;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInVisitLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExitWalkInReceptionQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitCareAssignmentService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInVisitLifecycleService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalkInVisitLifecycleServiceImpl implements WalkInVisitLifecycleService {
    private final CoordinationGuardService guard;
    private final ClinicVisitJpaRepository visits;
    private final WalkInVisitLifecycleRepository lifecycle;
    private final VisitAuditRecorder audit;
    private final ExitWalkInReceptionQueueService queueExit;
    private final ReceptionistAccountAccessService receptionists;
    private final ClinicalVisitCompletionEvidenceService clinical;
    private final DiagnosticVisitCompletionEvidenceService diagnostic;
    private final Clock clock;
    private final NotificationPublisher notifications;
    private final VisitReviewSourceService reviewSources;
    private final StaffAvailabilityProviderQueryService doctorNames;
    private final VisitExamQueueService examQueue;
    private final GetClinicServiceCatalogService catalog;
    private final ManageVisitCareAssignmentService careAssignments;
    private final DoctorProfileIdentityQueryService doctorProfiles;

    public WalkInVisitLifecycleServiceImpl(CoordinationGuardService guard, ClinicVisitJpaRepository visits,
            WalkInVisitLifecycleRepository lifecycle, VisitAuditRecorder audit,
            ExitWalkInReceptionQueueService queueExit, ReceptionistAccountAccessService receptionists,
            ClinicalVisitCompletionEvidenceService clinical, DiagnosticVisitCompletionEvidenceService diagnostic, Clock clock,
            NotificationPublisher notifications, VisitReviewSourceService reviewSources,
            StaffAvailabilityProviderQueryService doctorNames, VisitExamQueueService examQueue,
            GetClinicServiceCatalogService catalog, ManageVisitCareAssignmentService careAssignments,
            DoctorProfileIdentityQueryService doctorProfiles) {
        this.guard = guard;
        this.visits = visits;
        this.lifecycle = lifecycle;
        this.audit = audit;
        this.queueExit = queueExit;
        this.receptionists = receptionists;
        this.clinical = clinical;
        this.diagnostic = diagnostic;
        this.clock = clock;
        this.notifications = notifications;
        this.reviewSources = reviewSources;
        this.doctorNames = doctorNames;
        this.examQueue = examQueue;
        this.catalog = catalog;
        this.careAssignments = careAssignments;
        this.doctorProfiles = doctorProfiles;
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
        // Money taken for a walk-in cannot be returned here, so a walk-in whose fee was paid stays in the queue.
        var queued = examQueue.find(visitId);
        if (queued.isPresent() && !cancellableInQueue(queued.get())) {
            throw new VisitOperationException("VISIT_CANCELLATION_NOT_ALLOWED");
        }
        var cancelledAt = clock.instant();
        closeAndRecord(visitId, actorAccountId, actorRole, correlationId, "CANCELLED", cancelledAt, true);
        examQueue.cancel(visitId, cancelledAt);
    }

    /** Fee not yet paid (PENDING_PAYMENT), or a free service (WAITING from the start). Any other state is refused. */
    private boolean cancellableInQueue(ExamQueueEntry entry) {
        return switch (entry.examStatus()) {
            case PENDING_PAYMENT -> true;
            case WAITING -> isFreeService(entry.serviceId());
            default -> false;
        };
    }

    /**
     * A paid walk-in is WAITING too, so the catalog price tells the two apart. Known gap: a service whose price was
     * set to 0 after a walk-in was paid would look free. A missing service or price fails closed (not free).
     */
    private boolean isFreeService(UUID serviceId) {
        BigDecimal price = serviceId == null ? null : catalog.getService(serviceId).getPrice();
        return price != null && price.signum() == 0;
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
        if (!completionEvidenceSatisfied(visitId, visit)) throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        var closedAt = clock.instant();
        closeAndRecord(visitId, actorAccountId, actorRole, correlationId, "VISIT_CLOSED", closedAt, "WALK_IN".equals(visit.getSource()));
        inviteReview(visitId, visit, closedAt);
    }

    @Override
    @Transactional
    public VisitCompletionResult completeWalkIn(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId) {
        validate(visitId, actorAccountId, actorRole, correlationId);
        if (actorRole != AccountRole.DOCTOR) throw new VisitOperationException("ACCESS_DENIED");
        UUID doctorProfileId = doctorProfiles.findDoctorProfileId(actorAccountId)
                .orElseThrow(() -> new VisitOperationException("ACCESS_DENIED"));
        guard.acquire();
        var visit = visits.findByIdForUpdate(visitId)
                .orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        // A scheduled visit is closed through its appointment, never through this endpoint.
        if (!"WALK_IN".equals(visit.getSource())) throw new VisitOperationException("VISIT_EXAM_STATE_CONFLICT");
        return completeLocked(visitId, visit, doctorProfileId, actorAccountId, correlationId);
    }

    @Override
    @Transactional
    public VisitCompletionResult completeByDoctor(UUID visitId, UUID doctorProfileId, UUID actorAccountId,
            String correlationId) {
        validate(visitId, actorAccountId, AccountRole.DOCTOR, correlationId);
        if (doctorProfileId == null) throw new VisitOperationException("VALIDATION_FAILED");
        guard.acquire();
        var visit = visits.findByIdForUpdate(visitId)
                .orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        return completeLocked(visitId, visit, doctorProfileId, actorAccountId, correlationId);
    }

    /** Doctor completion on a locked visit: state, then evidence, then care, visit, queue and review invite. */
    private VisitCompletionResult completeLocked(UUID visitId, ClinicVisitJpaEntity visit, UUID doctorProfileId,
            UUID actorAccountId, String correlationId) {
        if (!"OPEN".equals(visit.getStatus())) throw new VisitOperationException("VISIT_EXAM_STATE_CONFLICT");
        requireExaminedBy(visitId, doctorProfileId);
        if (!completionEvidenceSatisfied(visitId, visit)) throw new VisitOperationException("VISIT_COMPLETION_NOT_ALLOWED");
        var closedAt = clock.instant();
        careAssignments.completeDoctorCare(visitId, doctorProfileId, actorAccountId, correlationId, closedAt);
        // Walk-in rows already left the reception queue when care started, so no reception exit happens here.
        closeAndRecord(visitId, actorAccountId, AccountRole.DOCTOR, correlationId, "VISIT_CLOSED", closedAt, false);
        examQueue.complete(visitId, doctorProfileId, closedAt);
        inviteReview(visitId, visit, closedAt);
        return new VisitCompletionResult(visitId, VisitExamStatus.COMPLETED, closedAt);
    }

    /** The queue entry must be this doctor's and must be under examination (IN_EXAMINATION or AWAITING_CONCLUSION). */
    private void requireExaminedBy(UUID visitId, UUID doctorProfileId) {
        ExamQueueEntry entry = examQueue.find(visitId)
                .orElseThrow(() -> new VisitOperationException("VISIT_EXAM_STATE_CONFLICT"));
        boolean examining = entry.examStatus() == VisitExamStatus.IN_EXAMINATION
                || entry.examStatus() == VisitExamStatus.AWAITING_CONCLUSION;
        if (!examining || !doctorProfileId.equals(entry.doctorProfileId())) {
            throw new VisitOperationException("VISIT_EXAM_STATE_CONFLICT");
        }
    }

    /**
     * Examination finished, every clinical order resolved, every imaging order completed. Shared by the receptionist
     * close (refused as VISIT_CARE_NOT_CHANGEABLE) and the doctor completion (refused as VISIT_COMPLETION_NOT_ALLOWED).
     */
    private boolean completionEvidenceSatisfied(UUID visitId, ClinicVisitJpaEntity visit) {
        // Guard and Visit row lock remain held through the owner read and commit.
        var evidence = clinical.getCompletionEvidence(visitId);
        if (evidence == null || !visitId.equals(evidence.visitId()) || !evidence.examinationExists()
                || !evidence.examinationCompleted() || evidence.outstandingOrderCount() != 0
                || !evidence.allNonCancelledOrdersHaveResults()) {
            return false;
        }
        if (evidence.imagingOrderIds().isEmpty()) return true;
        var imaging = diagnostic.getCompletionEvidence(visitId, visit.getPatientProfileId(), evidence.imagingOrderIds());
        return imaging != null && visitId.equals(imaging.visitId())
                && visit.getPatientProfileId().equals(imaging.patientId())
                && java.util.Set.copyOf(evidence.imagingOrderIds()).equals(imaging.requiredServiceOrderIds())
                && imaging.allComplete();
    }

    private void inviteReview(UUID visitId, ClinicVisitJpaEntity visit, java.time.Instant closedAt) {
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
        if ("CANCELLED".equals(reason)) {
            audit.walkInCancelled(actorAccountId, visitId, now, correlationId);
        } else if (walkIn) {
            audit.walkInClosed(actorAccountId, visitId, now, correlationId);
        } else {
            audit.visitClosed(actorAccountId, visitId, now, correlationId);
        }
    }

    private static void validate(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId) {
        if (visitId == null || actorAccountId == null || actorRole == null || correlationId == null
                || correlationId.isBlank() || correlationId.length() > 128) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
    }
}
