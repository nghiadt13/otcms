package fu.fa26.sep490.g58.otcms_be.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param timeoutMs connect/read/write timeout of every PayOS call; non-positive values fall back to
 *                  {@link #DEFAULT_TIMEOUT_MS} (the SDK default of 60s must never apply: settle queries PayOS)
 */
@ConfigurationProperties(prefix = "app.payment.payos")
public record PayOsProperties(boolean enabled, String clientId, String apiKey, String checksumKey,
        String returnUrl, String cancelUrl, long linkTtlSeconds, int timeoutMs) {
    public static final int DEFAULT_TIMEOUT_MS = 5000;

    public PayOsProperties {
        if (timeoutMs <= 0) timeoutMs = DEFAULT_TIMEOUT_MS;
    }
}
