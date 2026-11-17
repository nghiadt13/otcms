package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalUnperformedOrderService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ClinicalUnperformedOrderServiceImpl implements ClinicalUnperformedOrderService {
    private static final String CANCEL_KEY = "visit-incomplete";
    private final ServiceOrderRepository orders;
    private final ClinicalServiceOrderExecutionPort executions;
    private final Clock clock;

    public ClinicalUnperformedOrderServiceImpl(ServiceOrderRepository orders,
            ClinicalServiceOrderExecutionPort executions, Clock clock) {
        this.orders = orders;
        this.executions = executions;
        this.clock = clock;
    }

    @Override
    public List<UUID> cancelUnperformedOrders(UUID visitId, String reason) {
        if (visitId == null || reason == null || reason.isBlank()) throw new ClinicalOperationException("MSG-39");
        List<UUID> cancelled = new ArrayList<>();
        for (ServiceOrderJpaEntity order : orders.findByVisitId(visitId)) {
            if (order.getOrderStatus() != ServiceOrderStatus.ORDERED) continue;
            var request = executions.findForUpdate(order.getId());
            if (request.isPresent()) {
                // A dispatched imaging order: only a request nobody has started is unperformed.
                if (request.get().status() != ExecutionStatus.PENDING) continue;
                if (executions.cancel(order.getId(), request.get().executionVersion(), "CLINIC_UNABLE", reason,
                        CANCEL_KEY, sha256(reason)) != 1) continue;
            }
            order.cancel(reason, clock.instant());
            orders.save(order);
            cancelled.add(order.getId());
        }
        return List.copyOf(cancelled);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
