package fu.fa26.sep490.g58.otcms_be.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Typed, bounded authentication policy settings. */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
    private final RateLimit login;
    private final RateLimit register;
    private final RateLimit resend;
    private final RateLimit refresh;
    private final RateLimit googleExchange;
    private final RateLimit credentialFailure;
    private final String termsVersion;
    private final String privacyVersion;
    private final String rateLimitHmacSecret;
    @ConstructorBinding
    public AuthProperties(RateLimit login, RateLimit register, RateLimit resend, RateLimit refresh, RateLimit googleExchange, RateLimit credentialFailure, String termsVersion, String privacyVersion, String rateLimitHmacSecret) {
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
        this.login = login;
        this.register = register;
        this.resend = resend;
        this.refresh = refresh;
        this.googleExchange = googleExchange;
        this.credentialFailure = credentialFailure;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.rateLimitHmacSecret = rateLimitHmacSecret;
    }
    public RateLimit getLogin() { return login; }
    public RateLimit login() { return login; }
    public RateLimit getRegister() { return register; }
    public RateLimit register() { return register; }
    public RateLimit getResend() { return resend; }
    public RateLimit resend() { return resend; }
    public RateLimit getRefresh() { return refresh; }
    public RateLimit refresh() { return refresh; }
    public RateLimit getGoogleExchange() { return googleExchange; }
    public RateLimit googleExchange() { return googleExchange; }
    public RateLimit getCredentialFailure() { return credentialFailure; }
    public RateLimit credentialFailure() { return credentialFailure; }
    public String getTermsVersion() { return termsVersion; }
    public String termsVersion() { return termsVersion; }
    public String getPrivacyVersion() { return privacyVersion; }
    public String privacyVersion() { return privacyVersion; }
    public String getRateLimitHmacSecret() { return rateLimitHmacSecret; }
    public String rateLimitHmacSecret() { return rateLimitHmacSecret; }
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

        public static class RateLimit {
    private final int requests;
    private final int windowSeconds;
    public RateLimit(int requests, int windowSeconds) {
        if (requests < 1 || windowSeconds < 1) {
                throw new IllegalArgumentException("Rate limits must be positive");
            }
        this.requests = requests;
        this.windowSeconds = windowSeconds;
    }
    public int getRequests() { return requests; }
    public int requests() { return requests; }
    public int getWindowSeconds() { return windowSeconds; }
    public int windowSeconds() { return windowSeconds; }
}
}
