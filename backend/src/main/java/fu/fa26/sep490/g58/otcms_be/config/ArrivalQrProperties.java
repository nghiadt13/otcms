package fu.fa26.sep490.g58.otcms_be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.arrival-qr")
public record ArrivalQrProperties(String clinicId, String hmacSecret, long ttlSeconds, long refreshSeconds) {
    public ArrivalQrProperties {
        if (clinicId == null || clinicId.isBlank() || ttlSeconds != 60 || refreshSeconds != 30) {
            throw new IllegalArgumentException("Arrival QR requires a clinic id, 60-second TTL and 30-second refresh");
        }
    }

    public byte[] requiredSecret() {
        if (hmacSecret == null || hmacSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.arrival-qr.hmac-secret must contain at least 32 bytes");
        }
        return hmacSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
