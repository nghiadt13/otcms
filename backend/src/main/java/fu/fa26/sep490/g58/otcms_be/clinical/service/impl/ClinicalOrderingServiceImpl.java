package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalVisitStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalOrderingRoundRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalOrderingServiceImpl implements ClinicalOrderingService {
    private final fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard;
    private final ClinicalOrderPreparationService preparation;
    private final ClinicalOrderingTransactionService writer;
    private final ServiceOrderRepository orders;
    private final ClinicalOrderingRoundRepository rounds;
    private final Clock clock;
    private final fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderCancellationBillingPort cancellationBilling;

    public ClinicalOrderingServiceImpl(ClinicalOrderPreparationService preparation,
            ClinicalOrderingTransactionService writer, ServiceOrderRepository orders, ClinicalOrderingRoundRepository rounds,
            Clock clock, fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard,
            fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderCancellationBillingPort cancellationBilling) {
        this.guard = guard;
        this.preparation = preparation;
        this.writer = writer;
        this.orders = orders;
        this.rounds = rounds;
        this.clock = clock;
        this.cancellationBilling = cancellationBilling;
    }

    @Override
    @Transactional
    public SubmitRoundResult submit(SubmitRound command) {
        if (command == null) throw new ClinicalOperationException("MSG-39");
        guard.acquire();
        var context = preparation.requireCurrentCare(command.visitId(), command.doctorId());
        var snapshots = preparation.prepare(command.orders(), false);
        var round = writer.createRound(command.visitId(), context.patientId(), command.doctorId(), snapshots);
        // Legacy compatibility fields only: Visit lifecycle is OPEN/CLOSED; coordination is separate.
        return new SubmitRoundResult(round, ClinicalVisitStatus.OTHER, false);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderView getImagingOrder(UUID orderId) {
        var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-04"));
        if (order.getServiceType() != ClinicalServiceType.IMAGING
                || order.getOrderStatus() == ServiceOrderStatus.CANCELLED) {
            throw new ClinicalOperationException("MSG-08");
        }
        return view(order);
    }

    @Override
    @Transactional
    public void markImagingResultAvailable(UUID orderId) {
        var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-04"));
        if (order.getServiceType() != ClinicalServiceType.IMAGING) throw new ClinicalOperationException("MSG-08");
        try {
            order.markResult(clock.instant());
        } catch (IllegalStateException exception) {
            throw new ClinicalOperationException("MSG-08");
        }
        orders.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLatestRoundReadyForReview(UUID visitId) {
        var latest = rounds.findTopByVisitIdOrderByRoundNumberDesc(visitId);
        if (latest.isEmpty()) return false;
        List<ServiceOrderJpaEntity> effective = orders.findByOrderingRoundId(latest.get().getId()).stream()
                .filter(order -> order.getOrderStatus() != ServiceOrderStatus.CANCELLED).toList();
        return !effective.isEmpty()
                && effective.stream().allMatch(order -> order.getOrderStatus() == ServiceOrderStatus.RESULTS_AVAILABLE);
    }

    @Override
    @Transactional
    public OrderView cancel(UUID orderId, String reason) {
        if (reason == null || reason.isBlank()) throw new ClinicalOperationException("MSG-08");
        guard.acquire();
        var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-04"));
        try {
            order.cancel(reason, clock.instant());
        } catch (IllegalStateException exception) {
            throw new ClinicalOperationException("MSG-08");
        }
        return view(orders.save(order));
    }

    @Override
    @Transactional
    public OrderView cancelPendingPayment(UUID orderId, UUID doctorId, String reason) {
        if (reason == null || reason.isBlank()) throw new ClinicalOperationException("MSG-39");
        var initial = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (!doctorId.equals(initial.getRequestingDoctorId())) throw new ClinicalOperationException("MSG-40");
        if (initial.getPaymentStatus() != ServiceOrderPaymentStatus.PENDING_PAYMENT
                || initial.getOrderStatus() != ServiceOrderStatus.ORDERED) {
            throw new ClinicalOperationException("MSG-42");
        }
        // Billing locks invoice/items before the shared coordination guard, matching payment settlement.
        cancellationBilling.voidUnpaidOrder(orderId);
        guard.acquire();
        var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (!doctorId.equals(order.getRequestingDoctorId())) throw new ClinicalOperationException("MSG-40");
        preparation.requireCurrentCare(order.getVisitId(), doctorId);
        if (order.getPaymentStatus() != ServiceOrderPaymentStatus.PENDING_PAYMENT
                || order.getOrderStatus() != ServiceOrderStatus.ORDERED) {
            throw new ClinicalOperationException("MSG-42");
        }
        try {
            order.cancel(reason.trim(), clock.instant());
        } catch (IllegalStateException exception) {
            throw new ClinicalOperationException("MSG-42");
        }
        return view(orders.save(order));
    }

    private OrderView view(ServiceOrderJpaEntity order) {
        return new OrderView(order.getId(), order.getOrderCode(), order.getVisitId(), order.getPatientId(),
                order.getOrderingRoundId(), order.getServiceId(), order.getServiceNameSnapshot(),
                order.getServiceType(), order.getRequestingDoctorId(), order.getClinicalReason(), order.getBodyArea(),
                order.getInstructions(), order.getPriceSnapshot(), order.getOrderStatus(), order.getPaymentStatus());
    }
}
