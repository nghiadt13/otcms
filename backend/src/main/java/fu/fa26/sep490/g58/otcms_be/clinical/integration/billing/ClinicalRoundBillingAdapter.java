package fu.fa26.sep490.g58.otcms_be.clinical.integration.billing;

import fu.fa26.sep490.g58.otcms_be.billing.integration.ClinicalRoundBillingPort;
import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.OrderingRoundRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClinicalRoundBillingAdapter implements ClinicalRoundBillingPort {
    private final OrderingRoundRepository rounds;
    private final ServiceOrderRepository orders;
    private final CoordinationGuardService guard;
    private final Clock clock;
    public ClinicalRoundBillingAdapter(OrderingRoundRepository rounds, ServiceOrderRepository orders,
            CoordinationGuardService guard, Clock clock) {
        this.rounds = rounds; this.orders = orders; this.guard = guard; this.clock = clock;
    }
    @Override
    @Transactional
    public ClinicalRoundBillingSource requirePayableRound(UUID roundId) {
        guard.acquire();
        var round = rounds.findById(roundId).orElseThrow(() -> new BillingException("SOURCE_NOT_BILLABLE"));
        var effective = orders.findByOrderingRoundId(roundId).stream()
                .filter(order -> order.getOrderStatus() != ServiceOrderStatus.CANCELLED)
                .sorted(java.util.Comparator.comparing(order -> order.getId().toString()))
                .map(order -> new PayableOrder(order.getId(), order.getServiceId(),
                        order.getServiceNameSnapshot(), order.getPriceSnapshot())).toList();
        if (effective.isEmpty()) throw new BillingException("SOURCE_NOT_BILLABLE");
        return new ClinicalRoundBillingSource(roundId, round.getVisitId(), round.getPatientId(), effective);
    }
    @Override
    @Transactional
    public void markRoundPaid(UUID roundId) {
        guard.acquire();
        if (!rounds.existsById(roundId)) throw new BillingException("SOURCE_NOT_BILLABLE");
        var effective = orders.findByOrderingRoundId(roundId).stream()
                .filter(order -> order.getOrderStatus() != ServiceOrderStatus.CANCELLED).toList();
        effective.forEach(order -> order.markPaid(clock.instant()));
        // Complete the owner write before returning, including when settlement joins an outer transaction.
        orders.saveAllAndFlush(effective);
    }
}
