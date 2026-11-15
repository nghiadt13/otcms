package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns only the local atomic write of an ordering round and its service orders. */
@Service
public class ClinicalOrderingTransactionService {
    public record OrderSnapshot(ClinicalOrderingService.OrderRequest request,
            ClinicServiceCatalogPort.ClinicalServiceSnapshot service) { }

    private final ServiceOrderRepository orders;
    private final Clock clock;
    private final fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard;
    private final ClinicalOrderPreparationService preparation;

    public ClinicalOrderingTransactionService(ServiceOrderRepository orders, Clock clock,
            fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard,
            ClinicalOrderPreparationService preparation) {
        this.orders = orders;
        this.clock = clock;
        this.guard = guard;
        this.preparation = preparation;
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
            throw new fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException("MSG-39");
        }
        int roundNumber = orders.findTopByVisitIdAndRoundHeadTrueOrderByRoundNumberDesc(visitId)
                .map(previous -> previous.getRoundNumber() + 1).orElse(1);
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        UUID roundId = UUID.randomUUID();
        var savedOrders = new ArrayList<ServiceOrderJpaEntity>();
        boolean head = true;
        for (var snapshot : snapshots) {
            var request = snapshot.request();
            var service = snapshot.service();
            savedOrders.add(orders.save(new ServiceOrderJpaEntity(newOrderCode(), roundId, roundNumber, head,
                    head ? submitKey : null, head ? submitFingerprint : null, visitId,
                    patientId, service.serviceId(), service.name(), service.type(), doctorId,
                    request.clinicalReason(), request.bodyArea(), request.instructions(),
                    service.currentPrice(), now)));
            head = false;
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
