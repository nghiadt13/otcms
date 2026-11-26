package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalOrderingRoundJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalOrderingRoundRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.idempotency.model.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.idempotency.service.IdempotencyService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns only the local atomic write of an ordering round and its service orders. */
@Service
public class ClinicalOrderingTransactionService {
    public record OrderSnapshot(ClinicalOrderingService.OrderRequest request,
            ClinicServiceCatalogPort.ClinicalServiceSnapshot service) { }

    private final ServiceOrderRepository orders;
    private final ClinicalOrderingRoundRepository rounds;
    private final Clock clock;
    private final fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard;
    private final ClinicalOrderPreparationService preparation;
    private final IdempotencyService store;

    public ClinicalOrderingTransactionService(ServiceOrderRepository orders, ClinicalOrderingRoundRepository rounds, Clock clock,
            fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard,
            ClinicalOrderPreparationService preparation, IdempotencyService store) {
        this.orders = orders;
        this.rounds = rounds;
        this.clock = clock;
        this.guard = guard;
        this.preparation = preparation;
        this.store = store;
    }

    @Transactional
    public ClinicalOrderingService.RoundView createRound(UUID visitId, UUID patientId, UUID doctorId,
            List<OrderSnapshot> snapshots) {
        return createRound(visitId, patientId, doctorId, snapshots, null, null);
    }

    @Transactional
    public ClinicalOrderingService.RoundView createRound(UUID visitId, UUID patientId, UUID doctorId,
            List<OrderSnapshot> snapshots, String submitKey, String submitFingerprint) {
        guard.acquire();
        // Preparation outside this transaction may have raced with UC28. Re-read after the guard.
        var context = preparation.requireCurrentCare(visitId, doctorId);
        if (!patientId.equals(context.patientId())) {
            throw new ClinicalOperationException("MSG-39");
        }
        Set<UUID> serviceIds = new HashSet<>();
        for (var snapshot : snapshots) {
            if (!serviceIds.add(snapshot.service().serviceId())) {
                throw new ClinicalOperationException("MSG-42");
            }
        }
        if (!orders.findByVisitIdAndServiceIdInAndOrderStatusNot(visitId, serviceIds,
                ServiceOrderStatus.CANCELLED).isEmpty()) {
            throw new ClinicalOperationException("MSG-42");
        }
        int roundNumber = rounds.findTopByVisitIdOrderByRoundNumberDesc(visitId)
                .map(previous -> previous.getRoundNumber() + 1).orElse(1);
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        UUID roundId = UUID.randomUUID();
        if (submitKey != null && !store.record(IdempotencyScope.ORDER_ROUND_SUBMIT, doctorId, submitKey, submitFingerprint, roundId)) {
            // A concurrent request committed this key first: roll back, and the caller replays that round.
            throw new DuplicateKeyException("ORDER_ROUND_SUBMIT key already recorded");
        }
        rounds.save(new ClinicalOrderingRoundJpaEntity(roundId, visitId, roundNumber, doctorId, now));
        var savedOrders = new ArrayList<ServiceOrderJpaEntity>();
        for (var snapshot : snapshots) {
            var request = snapshot.request();
            var service = snapshot.service();
            savedOrders.add(orders.save(new ServiceOrderJpaEntity(newOrderCode(), roundId,
                    visitId,
                    patientId, service.serviceId(), service.name(), service.type(), doctorId,
                    request.clinicalReason(), request.bodyArea(), request.instructions(),
                    service.currentPrice(), now)));
        }
        var views = savedOrders.stream().map(order -> new ClinicalOrderingService.OrderView(
                order.getId(), order.getOrderCode(), order.getVisitId(), order.getPatientId(),
                order.getOrderingRoundId(), order.getServiceId(), order.getServiceNameSnapshot(),
                order.getServiceType(), order.getRequestingDoctorId(), order.getClinicalReason(),
                order.getBodyArea(), order.getInstructions(), order.getPriceSnapshot(),
                order.getOrderStatus(), order.getPaymentStatus())).toList();
        return new ClinicalOrderingService.RoundView(roundId, visitId, roundNumber, doctorId, now, views);
    }

    private static String newOrderCode() {
        return "SO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 29);
    }
}
