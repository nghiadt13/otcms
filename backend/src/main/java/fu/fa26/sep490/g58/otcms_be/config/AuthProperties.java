package fu.fa26.sep490.g58.otcms_be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Typed, bounded authentication policy settings. */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        RateLimit login,
        RateLimit register,
        RateLimit resend,
        RateLimit refresh,
        RateLimit googleExchange,
        RateLimit credentialFailure,
        String termsVersion,
        String privacyVersion,
        String rateLimitHmacSecret) {

    @ConstructorBinding
    public AuthProperties {
        login = defaulted(login, 10, 60);
        register = defaulted(register, 5, 3600);
        resend = defaulted(resend, 3, 3600);
        refresh = defaulted(refresh, 30, 60);
        googleExchange = defaulted(googleExchange, 10, 60);
        credentialFailure = defaulted(credentialFailure, 5, 900);
        termsVersion = termsVersion == null || termsVersion.isBlank() ? "v1" : termsVersion;
        privacyVersion = privacyVersion == null || privacyVersion.isBlank() ? "v1" : privacyVersion;
        rateLimitHmacSecret = rateLimitHmacSecret == null || rateLimitHmacSecret.isBlank()
                ? System.getenv("APP_AUTH_RATE_LIMIT_HMAC_SECRET") : rateLimitHmacSecret;
    }

    /** Compatibility constructor for callers that do not configure the audit secret yet. */
    public AuthProperties(RateLimit login, RateLimit register, RateLimit resend, RateLimit refresh,
                          RateLimit googleExchange, RateLimit credentialFailure,
                          String termsVersion, String privacyVersion) {
        this(login, register, resend, refresh, googleExchange, credentialFailure,
                termsVersion, privacyVersion, null);
    }

    private static RateLimit defaulted(RateLimit value, int requests, int windowSeconds) {
        return value == null ? new RateLimit(requests, windowSeconds) : value;
    }

    public record RateLimit(int requests, int windowSeconds) {
        public RateLimit {
            if (requests < 1 || windowSeconds < 1) {
                throw new IllegalArgumentException("Rate limits must be positive");
            }
        }
    }
}
