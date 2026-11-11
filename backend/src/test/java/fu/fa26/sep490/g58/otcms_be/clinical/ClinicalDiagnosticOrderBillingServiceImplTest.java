package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalDiagnosticOrderBillingServiceImpl;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticPaymentPublicationService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticRefundCancellationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalDiagnosticOrderBillingServiceImplTest {
    private final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    private final VisitClinicalContextPort visits = mock(VisitClinicalContextPort.class);
    private final ClinicalDiagnosticOrderBillingServiceImpl service = new ClinicalDiagnosticOrderBillingServiceImpl(
            orders, visits, mock(DiagnosticPaymentPublicationService.class),
            mock(DiagnosticRefundCancellationService.class), Clock.systemUTC());

    @Test
    void listsOnlyPendingImagingOrdersForActiveMatchingVisit() {
        UUID patientId = UUID.randomUUID();
        UUID activeVisitId = UUID.randomUUID();
        UUID closedVisitId = UUID.randomUUID();
        var activeOrder = order(activeVisitId, patientId, ClinicalServiceType.IMAGING);
        var closedOrder = order(closedVisitId, patientId, ClinicalServiceType.IMAGING);
        when(orders.findAllByPatientIdAndServiceTypeAndPaymentStatusAndOrderStatusOrderByCreatedAtAscIdAsc(
                patientId, ClinicalServiceType.IMAGING, ServiceOrderPaymentStatus.PENDING_PAYMENT,
                ServiceOrderStatus.ORDERED)).thenReturn(List.of(activeOrder, closedOrder));
        when(visits.getVisitContext(activeVisitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                activeVisitId, patientId, UUID.randomUUID(), true));
        when(visits.getVisitContext(closedVisitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                closedVisitId, patientId, UUID.randomUUID(), false));

        var result = service.listPendingImagingOrders(patientId);

        assertEquals(1, result.size());
        assertEquals(activeOrder.getId(), result.getFirst().orderId());
        assertEquals(activeVisitId, result.getFirst().visitId());
        assertEquals(patientId, result.getFirst().patientProfileId());
    }

    private static ServiceOrderJpaEntity order(UUID visitId, UUID patientId, ClinicalServiceType type) {
        return new ServiceOrderJpaEntity("SO-" + UUID.randomUUID(), UUID.randomUUID(), visitId, patientId,
                UUID.randomUUID(), "MRI", type, UUID.randomUUID(), "Đau gối", "RIGHT_KNEE", null,
                new BigDecimal("100000"), Instant.parse("2026-11-07T08:00:00Z"));
    }
}
