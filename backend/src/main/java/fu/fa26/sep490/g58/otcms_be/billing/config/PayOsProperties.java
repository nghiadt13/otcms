package fu.fa26.sep490.g58.otcms_be.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.payos")
public record PayOsProperties(boolean enabled, String clientId, String apiKey, String checksumKey,
        String returnUrl, String cancelUrl, long linkTtlSeconds) { }
