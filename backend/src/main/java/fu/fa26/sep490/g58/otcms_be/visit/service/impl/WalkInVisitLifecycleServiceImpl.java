package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
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
    private final DoctorAccountAccessService doctors;
    private final Clock clock;

    public WalkInVisitLifecycleServiceImpl(CoordinationGuardService guard, ClinicVisitJpaRepository visits,
            WalkInVisitLifecycleRepository lifecycle, VisitAuditJpaRepository audits,
            ExitWalkInReceptionQueueService queueExit, ReceptionistAccountAccessService receptionists,
            DoctorAccountAccessService doctors, Clock clock) {
        this.guard = guard;
        this.visits = visits;
        this.lifecycle = lifecycle;
        this.audits = audits;
        this.queueExit = queueExit;
        this.receptionists = receptionists;
        this.doctors = doctors;
        this.clock = clock;
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
        closeAndRecord(visitId, actorAccountId, actorRole, correlationId, "CANCELLED", clock.instant());
    }

    @Override
    @Transactional
    public void close(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId) {
        validate(visitId, actorAccountId, actorRole, correlationId);
        if (actorRole != AccountRole.DOCTOR || !doctors.isActiveDoctorAccount(actorAccountId)) {
            throw new VisitOperationException("ACCESS_DENIED");
        }
        guard.acquire();
        var visit = visits.findByIdForUpdate(visitId)
                .orElseThrow(() -> new VisitOperationException("RESOURCE_NOT_FOUND"));
        if (!"WALK_IN".equals(visit.getSource()) || !"OPEN".equals(visit.getStatus())
                || lifecycle.hasInProgressCare(visitId)) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        closeAndRecord(visitId, actorAccountId, actorRole, correlationId, "VISIT_CLOSED", clock.instant());
    }

    private void closeAndRecord(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId,
            String reason, java.time.Instant now) {
        queueExit.exit(new ExitWalkInReceptionQueueCommand(visitId, actorAccountId, actorRole,
                reason, now, correlationId));
        if (!lifecycle.closeOpenWalkIn(visitId, now)) {
            throw new VisitOperationException("VISIT_CARE_NOT_CHANGEABLE");
        }
        lifecycle.releaseReservedCare(visitId, now);
        VisitAuditJpaEntity audit = "CANCELLED".equals(reason)
                ? VisitAuditJpaEntity.walkInCancelled(actorAccountId, visitId, now, correlationId)
                : VisitAuditJpaEntity.walkInClosed(actorAccountId, visitId, now, correlationId);
        audits.saveAndFlush(audit);
    }

    private static void validate(UUID visitId, UUID actorAccountId, AccountRole actorRole, String correlationId) {
        if (visitId == null || actorAccountId == null || actorRole == null || correlationId == null
                || correlationId.isBlank() || correlationId.length() > 128) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
    }
}
