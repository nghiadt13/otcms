package fu.fa26.sep490.g58.otcms_be.clinical.integration.billing;

import fu.fa26.sep490.g58.otcms_be.billing.service.BillingRefundService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderCancellationBillingPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ClinicalOrderCancellationBillingAdapter implements ClinicalOrderCancellationBillingPort {
    private final BillingRefundService billing;

    public ClinicalOrderCancellationBillingAdapter(BillingRefundService billing) {
        this.billing = billing;
    }

    @Override
    public void voidUnpaidOrder(UUID orderId) {
        billing.voidUnpaidServiceOrder(orderId);
    }
}
