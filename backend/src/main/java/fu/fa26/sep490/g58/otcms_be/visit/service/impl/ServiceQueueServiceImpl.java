package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.service.BillingQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderQueueService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderQueueService.QueueOrder;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ServiceQueueRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceAdmissionHandler;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceQueueService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 08 room queue rules. A patient is in at most one SERVICE queue at a time: the entry of the first paid order
 * is created at once, the next order's entry only when the previous stay ends, in the room that order was assigned.
 */
@Service
public class ServiceQueueServiceImpl implements ServiceQueueService {
    private static final Logger log = LoggerFactory.getLogger(ServiceQueueServiceImpl.class);
    private final ClinicalServiceOrderQueueService orders;
    private final BillingQueryService billing;
    private final List<ServiceAdmissionHandler> handlers;
    private final ServiceQueueRepository queue;
    private final ClinicVisitJpaRepository visits;
    private final VisitRoomJpaRepository rooms;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public ServiceQueueServiceImpl(ClinicalServiceOrderQueueService orders, BillingQueryService billing,
            List<ServiceAdmissionHandler> handlers, ServiceQueueRepository queue, ClinicVisitJpaRepository visits,
            VisitRoomJpaRepository rooms, AppointmentBookingProperties booking, Clock clock) {
        this.orders = orders;
        this.billing = billing;
        this.handlers = handlers;
        this.queue = queue;
        this.visits = visits;
        this.rooms = rooms;
        this.booking = booking;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void admitPaidOrder(UUID serviceOrderId) {
        QueueOrder order = orders.find(serviceOrderId).orElse(null);
        if (order == null || !"ORDERED".equals(order.orderStatus())) return;
        ServiceAdmissionHandler handler = handlerFor(order.serviceType()).orElse(null);
        if (handler == null || !billing.isServiceOrderPaid(serviceOrderId)) return;
        ClinicVisitJpaEntity visit = visits.findByIdForUpdate(order.visitId()).orElse(null);
        if (visit == null || !"OPEN".equals(visit.getStatus())) return;
        orders.markPaid(serviceOrderId);
        if (order.roomId() == null) {
            UUID room = leastWaitingRoom(handler.eligibleRoomIds(serviceOrderId));
            if (room == null) {
                log.warn("Paid service order {} has no eligible room; reception must assign one", serviceOrderId);
                return;
            }
            handler.assignRoom(serviceOrderId, room);
        }
        promoteNext(visit);
    }

    @Override
    @Transactional
    public void enqueueDispatchedOrder(UUID serviceOrderId) {
        QueueOrder order = orders.find(serviceOrderId).orElse(null);
        if (order == null || !"ORDERED".equals(order.orderStatus()) || order.roomId() == null
                || handlerFor(order.serviceType()).isEmpty() || !billing.isServiceOrderPaid(serviceOrderId)) return;
        ClinicVisitJpaEntity visit = visits.findByIdForUpdate(order.visitId()).orElse(null);
        if (visit != null && "OPEN".equals(visit.getStatus())) promoteNext(visit);
    }

    @Override
    @Transactional
    public void markInProgress(UUID serviceOrderId) {
        QueueEntryJpaEntity entry = queue.findServiceByOrder(serviceOrderId).orElse(null);
        if (entry == null) return;
        lockVisit(entry.getVisitId());
        switch (entry.getStatus()) {
            case "CALLED" -> entry.startService(clock.instant());
            case "WAITING", "ABSENT" -> throw new VisitOperationException("MSG-42");
            default -> { }
        }
    }

    @Override
    @Transactional
    public void complete(UUID serviceOrderId) {
        finish(serviceOrderId, true);
    }

    @Override
    @Transactional
    public void leave(UUID serviceOrderId) {
        finish(serviceOrderId, false);
    }

    private void finish(UUID serviceOrderId, boolean done) {
        QueueEntryJpaEntity entry = queue.findServiceByOrder(serviceOrderId).orElse(null);
        if (entry == null || !ServiceQueueRepository.OPEN.contains(entry.getStatus())) return;
        ClinicVisitJpaEntity visit = lockVisit(entry.getVisitId());
        Instant now = clock.instant();
        if (done) entry.closeStay(now); else entry.cancelStay(now);
        queue.flush();
        if (visit != null && "OPEN".equals(visit.getStatus())) promoteNext(visit);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeEntriesOfVisit(UUID visitId) {
        Instant now = clock.instant();
        queue.findOpenServiceByVisit(visitId, ServiceQueueRepository.OPEN).forEach(entry -> entry.cancelStay(now));
        queue.findOpenResultReviewByVisit(visitId, ServiceQueueRepository.OPEN).forEach(entry -> entry.closeStay(now));
    }

    private ClinicVisitJpaEntity lockVisit(UUID visitId) {
        return visits.findByIdForUpdate(visitId).orElse(null);
    }

    /**
     * Creates the entry of the next paid, roomed order that has none yet, unless the patient already waits somewhere.
     * Picks the eligible order whose assigned room currently has the fewest waiting patients, preferring LABORATORY
     * first on a tie (Examination Flow).
     */
    private void promoteNext(ClinicVisitJpaEntity visit) {
        UUID visitId = visit.getId();
        if (!queue.findOpenServiceByVisit(visitId, ServiceQueueRepository.OPEN).isEmpty()) return;
        Set<UUID> queued = new HashSet<>(queue.findQueuedOrderIds(visitId));
        Optional<QueueOrder> next = orders.findByVisit(visitId).stream()
                .filter(order -> handlerFor(order.serviceType()).isPresent())
                .filter(order -> "ORDERED".equals(order.orderStatus()) && order.roomId() != null
                        && !"CANCELLED".equals(order.executionStatus()) && !queued.contains(order.orderId()))
                .filter(order -> billing.isServiceOrderPaid(order.orderId()))
                .min(Comparator
                        .comparingLong((QueueOrder order) -> queue.countOpenInRoom(
                                order.roomId(), QueueEntryJpaEntity.STAGE_SERVICE, ServiceQueueRepository.OPEN))
                        .thenComparingInt(order -> "LABORATORY".equals(order.serviceType()) ? 0 : 1));
        next.ifPresent(order -> enqueue(visitId, order));
    }

    private void enqueue(UUID visitId, QueueOrder order) {
        UUID roomId = order.roomId();
        rooms.findByIdForUpdate(roomId).orElseThrow(() -> new VisitOperationException("MSG-41"));
        Instant now = clock.instant();
        LocalDate day = now.atZone(booking.clinicTimeZone()).toLocalDate();
        int number = queue.maxNumber(roomId, QueueEntryJpaEntity.STAGE_SERVICE, day) + 1;
        // A patient who stays in the same room for the next order keeps the place of the stay that just ended.
        long sortOrder = queue.findFinishedServiceByVisit(visitId).stream().findFirst()
                .filter(previous -> roomId.equals(previous.getRoomId()))
                .map(QueueEntryJpaEntity::getSortOrder).orElse(now.toEpochMilli());
        queue.save(QueueEntryJpaEntity.service(visitId, order.orderId(), roomId, order.serviceId(), day, number, sortOrder, now));
    }

    private UUID leastWaitingRoom(List<UUID> candidates) {
        return candidates.stream().min(Comparator.comparingLong(room ->
                queue.countOpenInRoom(room, QueueEntryJpaEntity.STAGE_SERVICE, ServiceQueueRepository.OPEN))).orElse(null);
    }

    private Optional<ServiceAdmissionHandler> handlerFor(String serviceType) {
        return handlers.stream().filter(handler -> handler.supports(serviceType)).findFirst();
    }
}
