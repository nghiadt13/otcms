package fu.fa26.sep490.g58.otcms_be.billing.service;

import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayRegistry {
    private final Map<String, PaymentGateway> gateways;

    public PaymentGatewayRegistry(java.util.List<PaymentGateway> gateways) {
        this.gateways = gateways.stream().collect(Collectors.toUnmodifiableMap(
                gateway -> gateway.provider().toUpperCase(java.util.Locale.ROOT), Function.identity()));
    }

    public PaymentGateway require(String provider) {
        PaymentGateway gateway = provider == null ? null
                : gateways.get(provider.toUpperCase(java.util.Locale.ROOT));
        if (gateway == null) throw new BillingException("GATEWAY_UNAVAILABLE");
        return gateway;
    }
}
