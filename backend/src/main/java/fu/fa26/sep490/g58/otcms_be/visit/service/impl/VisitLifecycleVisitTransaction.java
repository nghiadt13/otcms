package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.service.BillingRefundService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalUnperformedOrderService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInReceptionQueueEntryJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInVisitLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One visit decided by a nightly sweep, in one transaction. Kept in its own bean so the sweep can call it per visit
 * through the Spring proxy (own transaction each, one failure never rolls back the others).
 */
@Service
public class VisitLifecycleVisitTransaction {
    static final String CANCEL_REASON = "Lượt khám chưa hoàn tất sau 7 ngày";
    /** Length of the period of a visit in days, counted from the day of the first check-in, that day included. */
    static final int PERIOD_DAYS = 7;

    private final CoordinationGuardService guard;
    private final ClinicVisitJpaRepository visits;
    private final VisitLifecycleRepository lifecycle;
    private final WalkInVisitLifecycleRepository visitWrites;
    private final WalkInReceptionQueueEntryJpaRepository receptionQueue;
    private final VisitExamQueueService examQueue;
    private final ClinicalUnperformedOrderService unperformedOrders;
    private final ClinicalVisitCompletionEvidenceService evidence;
    private final BillingRefundService billing;
    private final NotificationPublisher notifications;
    private final PatientClinicalIdentityQueryService identities;
    private final VisitAuditRecorder audit;
    private final AppointmentBookingProperties booking;

    public VisitLifecycleVisitTransaction(CoordinationGuardService guard, ClinicVisitJpaRepository visits,
            VisitLifecycleRepository lifecycle, WalkInVisitLifecycleRepository visitWrites,
            WalkInReceptionQueueEntryJpaRepository receptionQueue, VisitExamQueueService examQueue,
            ClinicalUnperformedOrderService unperformedOrders, ClinicalVisitCompletionEvidenceService evidence,
            BillingRefundService billing, NotificationPublisher notifications,
            PatientClinicalIdentityQueryService identities, VisitAuditRecorder audit,
            AppointmentBookingProperties booking) {
        this.guard = guard;
        this.visits = visits;
        this.lifecycle = lifecycle;
        this.visitWrites = visitWrites;
        this.receptionQueue = receptionQueue;
        this.examQueue = examQueue;
        this.unperformedOrders = unperformedOrders;
        this.evidence = evidence;
        this.billing = billing;
        this.notifications = notifications;
        this.identities = identities;
        this.audit = audit;
        this.booking = booking;
    }

    /** First day on which a visit checked in on {@code checkInDay} is Incomplete: day 8, counting the check-in day. */
    static boolean periodEnded(Instant firstCheckIn, Instant now, java.time.ZoneId zone) {
        LocalDate lastDay = firstCheckIn.atZone(zone).toLocalDate().plusDays(PERIOD_DAYS - 1L);
        return now.atZone(zone).toLocalDate().isAfter(lastDay);
    }

    /**
     * BR-65: an OPEN visit Awaiting Conclusion whose period ended becomes INCOMPLETE. Unperformed orders are
     * cancelled (unpaid fee voided, paid fee kept: BR-53), the open room entries and care are closed and the
     * patient gets MSG-82. Anything else is left alone, so a second call changes nothing.
     */
    @Transactional
    public boolean markIncompleteIfDue(UUID visitId, Instant now) {
        guard.acquire();
        var visit = visits.findByIdForUpdate(visitId).orElse(null);
        if (visit == null || !"OPEN".equals(visit.getStatus())
                || !periodEnded(visit.getCheckedInAt(), now, booking.clinicTimeZone())) return false;
        var exam = examQueue.find(visitId).orElse(null);
        if (exam == null || exam.examStatus() != VisitExamStatus.AWAITING_CONCLUSION) return false;

        List<UUID> cancelled = unperformedOrders.cancelUnperformedOrders(visitId, CANCEL_REASON);
        cancelled.forEach(billing::voidUnpaidServiceOrder);
        lifecycle.closeServiceEntries(visitId, now);
        lifecycle.releaseOpenCare(visitId, now);
        if (!lifecycle.markIncomplete(visitId, now)) return false;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cancelledOrders", cancelled.size());
        audit.lifecycleEvent("VISIT_INCOMPLETE", "VISIT", visitId, visitId, null, now, metadata,
                "visit-incomplete-sweep");
        var identity = identities.findClinicalIdentities(List.of(visit.getPatientProfileId()))
                .get(visit.getPatientProfileId());
        notifications.publishToPatient(visit.getPatientProfileId(), NotificationMessages.visitIncomplete(visitId,
                visit.getCheckedInAt(), identity == null ? null : identity.fullName()));
        return true;
    }

    /**
     * NF-05(a): the day ended. Waiting visits are Left (exam entry CANCELLED, visit CLOSED); a visit in examination
     * becomes Awaiting Conclusion when its examination was saved or services were ordered, otherwise Left; open
     * reception and room entries are closed. An Awaiting Conclusion visit stays open for its 7-day period.
     */
    @Transactional
    public boolean closeEndOfDay(UUID visitId, Instant now) {
        guard.acquire();
        var visit = visits.findByIdForUpdate(visitId).orElse(null);
        if (visit == null || !"OPEN".equals(visit.getStatus())) return false;
        boolean changed = lifecycle.closeServiceEntries(visitId, now) > 0;
        for (UUID entryId : receptionQueue.findWaitingReceptionIdsForUpdate(visitId)) {
            changed |= receptionQueue.leaveReception(entryId, now, "VISIT_CLOSED") == 1;
        }
        var exam = examQueue.find(visitId).orElse(null);
        VisitExamStatus status = exam == null ? null : exam.examStatus();
        if (status == VisitExamStatus.IN_EXAMINATION && examinationOrOrdersRecorded(visitId)) {
            examQueue.markAwaitingConclusion(visitId, now);
            audit.lifecycleEvent("VISIT_AWAITING_CONCLUSION", "VISIT", visitId, visitId, null, now, Map.of(),
                    "visit-end-of-day");
            return true;
        }
        if (status == VisitExamStatus.PENDING_PAYMENT || status == VisitExamStatus.WAITING
                || status == VisitExamStatus.CALLED || status == VisitExamStatus.ABSENT
                || status == VisitExamStatus.IN_EXAMINATION) {
            examQueue.cancel(visitId, now);
            lifecycle.releaseOpenCare(visitId, now);
            visitWrites.closeOpenVisit(visitId, now);
            audit.lifecycleEvent("VISIT_LEFT", "VISIT", visitId, visitId, null, now,
                    Map.of("examStatus", status.name()), "visit-end-of-day");
            return true;
        }
        return changed;
    }

    private boolean examinationOrOrdersRecorded(UUID visitId) {
        var facts = evidence.getCompletionEvidence(visitId);
        return facts != null && (facts.examinationExists() || facts.activeOrderCount() > 0);
    }
}
