package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderQueueService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos.ReturnCandidate;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos.VisitQueueTicket;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ServiceQueueRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReturnService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceAdmissionHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VisitReturnServiceImpl implements VisitReturnService {
    private final ReceptionistAccountAccessService receptionists;
    private final ClinicVisitJpaRepository visits;
    private final VisitLifecycleRepository lifecycle;
    private final VisitLifecycleVisitTransaction incompleteSweep;
    private final VisitExamQueueService examQueue;
    private final CoordinationGuardService guard;
    private final DoctorProfileIdentityQueryService doctorProfiles;
    private final VisitRoomJpaRepository rooms;
    private final VisitAuditRecorder audit;
    private final AppointmentBookingProperties booking;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ClinicalServiceOrderQueueService orders;
    private final ClinicalOrderingService ordering;
    private final BillingQueryService billing;
    private final ServiceQueueRepository queue;
    private final List<ServiceAdmissionHandler> handlers;

    public VisitReturnServiceImpl(ReceptionistAccountAccessService receptionists, ClinicVisitJpaRepository visits,
            VisitLifecycleRepository lifecycle, VisitLifecycleVisitTransaction incompleteSweep,
            VisitExamQueueService examQueue, CoordinationGuardService guard,
            DoctorProfileIdentityQueryService doctorProfiles, VisitRoomJpaRepository rooms, VisitAuditRecorder audit,
            AppointmentBookingProperties booking, PlatformTransactionManager transactionManager, Clock clock,
            ClinicalServiceOrderQueueService orders, ClinicalOrderingService ordering, BillingQueryService billing,
            ServiceQueueRepository queue, List<ServiceAdmissionHandler> handlers) {
        this.receptionists = receptionists;
        this.visits = visits;
        this.lifecycle = lifecycle;
        this.incompleteSweep = incompleteSweep;
        this.examQueue = examQueue;
        this.guard = guard;
        this.doctorProfiles = doctorProfiles;
        this.rooms = rooms;
        this.audit = audit;
        this.booking = booking;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.orders = orders;
        this.ordering = ordering;
        this.billing = billing;
        this.queue = queue;
        this.handlers = handlers;
    }

    @Override
    public Optional<ReturnCandidate> findCandidate(UUID patientProfileId, AuthenticatedAccount actor) {
        requireReceptionist(actor);
        if (patientProfileId == null) throw new VisitOperationException("MSG-39");
        var zone = booking.clinicTimeZone();
        // The check-in day counts as day 1, so the oldest visit still inside its period checked in 6 days ago.
        var earliest = clock.instant().atZone(zone).toLocalDate().minusDays(VisitLifecycleVisitTransaction.PERIOD_DAYS - 1L);
        return lifecycle.findReturnCandidate(patientProfileId, earliest, zone).map(row -> {
            Instant firstCheckIn = toInstant(row[1]);
            UUID doctorId = (UUID) row[2];
            UUID roomId = (UUID) row[3];
            return new ReturnCandidate((UUID) row[0], firstCheckIn,
                    firstCheckIn.atZone(zone).toLocalDate().plusDays(VisitLifecycleVisitTransaction.PERIOD_DAYS - 1L),
                    doctorId, doctorName(doctorId), roomId, roomLabel(roomId));
        });
    }

    @Override
    public VisitQueueTicket returnToVisit(UUID visitId, AuthenticatedAccount actor, String correlationId) {
        requireReceptionist(actor);
        if (visitId == null) throw new VisitOperationException("MSG-39");
        Instant now = clock.instant();
        var visit = visits.findById(visitId).orElseThrow(() -> new VisitOperationException("MSG-41"));
        if ("OPEN".equals(visit.getStatus())
                && VisitLifecycleVisitTransaction.periodEnded(visit.getCheckedInAt(), now, booking.clinicTimeZone())) {
            // AF3, check on read: the nightly sweep has not run yet, so close the period now (own transaction).
            incompleteSweep.markIncompleteIfDue(visitId, now);
            throw new VisitOperationException("MSG-42");
        }
        if (!"OPEN".equals(visit.getStatus())) throw new VisitOperationException("MSG-42");
        return transactions.execute(status -> {
            guard.acquire();
            var locked = visits.findByIdForUpdate(visitId).orElseThrow(() -> new VisitOperationException("MSG-41"));
            if (!"OPEN".equals(locked.getStatus())) throw new VisitOperationException("MSG-42");
            var exam = examQueue.find(visitId).orElseThrow(() -> new VisitOperationException("MSG-42"));
            if (exam.examStatus() != VisitExamStatus.AWAITING_CONCLUSION) {
                throw new VisitOperationException("MSG-42");
            }
            var outstanding = orders.findByVisit(visitId).stream()
                    .filter(order -> "ORDERED".equals(order.orderStatus()))
                    .filter(order -> !"CANCELLED".equals(order.executionStatus()))
                    .toList();
            UUID roomId;
            int number;
            var day = now.atZone(booking.clinicTimeZone()).toLocalDate();
            var serviceEntries = queue.findOpenServiceByVisit(visitId, ServiceQueueRepository.OPEN);
            var reviewEntries = queue.findOpenResultReviewByVisit(visitId, ServiceQueueRepository.OPEN);
            if (!outstanding.isEmpty()) {
                if (!reviewEntries.isEmpty()) throw new VisitOperationException("MSG-42");
                var order = outstanding.stream().filter(o -> o.roomId() != null
                                && (o.executionStatus() == null || "PENDING".equals(o.executionStatus()))
                                && handlers.stream().anyMatch(h -> h.supports(o.serviceType()))
                                && billing.isServiceOrderPaid(o.orderId()))
                        .findFirst().orElseThrow(() -> new VisitOperationException("MSG-42"));
                roomId = order.roomId();
                rooms.findByIdForUpdate(roomId).orElseThrow(() -> new VisitOperationException("MSG-41"));
                number = queue.maxNumber(roomId, QueueEntryJpaEntity.STAGE_SERVICE, day) + 1;
                var previous = queue.findServiceByOrder(order.orderId()).orElse(null);
                if (serviceEntries.stream().anyMatch(entry -> previous == null || !entry.getId().equals(previous.getId()))) {
                    throw new VisitOperationException("MSG-42");
                }
                if (previous == null) {
                    queue.save(QueueEntryJpaEntity.service(visitId, order.orderId(), roomId, order.serviceId(),
                            day, number, now.toEpochMilli(), now));
                } else if ("CANCELLED".equals(previous.getStatus()) || "ABSENT".equals(previous.getStatus())
                        || ("WAITING".equals(previous.getStatus()) && !day.equals(previous.getClinicDate()))) {
                    previous.rejoinRoomQueue(roomId, day, number, now);
                } else {
                    throw new VisitOperationException("MSG-42");
                }
            } else {
                if (!serviceEntries.isEmpty()) throw new VisitOperationException("MSG-42");
                if (!ordering.isLatestRoundReadyForReview(visitId) || exam.roomId() == null) {
                    throw new VisitOperationException("MSG-42");
                }
                roomId = exam.roomId();
                rooms.findByIdForUpdate(roomId).orElseThrow(() -> new VisitOperationException("MSG-41"));
                number = queue.maxNumber(roomId, QueueEntryJpaEntity.STAGE_RESULT_REVIEW, day) + 1;
                if (reviewEntries.isEmpty()) {
                    queue.save(QueueEntryJpaEntity.resultReview(visitId, exam.doctorProfileId(), roomId, day, number, now));
                } else {
                    var previous = reviewEntries.getFirst();
                    if (day.equals(previous.getClinicDate()) && !"ABSENT".equals(previous.getStatus())) {
                        throw new VisitOperationException("MSG-42");
                    }
                    previous.rejoinRoomQueue(roomId, day, number, now);
                }
            }
            audit.lifecycleEvent("VISIT_RETURNED", "VISIT", visitId, visitId, actor.accountId(), now,
                    Map.of("queueNumber", number, "doctorProfileId", exam.doctorProfileId().toString()),
                    correlationId);
            return new VisitQueueTicket(visitId, "WAITING", number, exam.doctorProfileId(),
                    doctorName(exam.doctorProfileId()), roomId, roomLabel(roomId));
        });
    }

    private void requireReceptionist(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.RECEPTIONIST
                || !receptionists.isActiveReceptionistAccount(actor.accountId())) {
            throw new VisitOperationException("MSG-40");
        }
    }

    private String doctorName(UUID doctorProfileId) {
        return doctorProfileId == null ? null : doctorProfiles.findDoctorNames(List.of(doctorProfileId)).get(doctorProfileId);
    }

    private String roomLabel(UUID roomId) {
        return roomId == null ? null : rooms.findById(roomId).map(VisitRoomJpaEntity::getDisplayLabel).orElse(null);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected database timestamp type");
    }
}
