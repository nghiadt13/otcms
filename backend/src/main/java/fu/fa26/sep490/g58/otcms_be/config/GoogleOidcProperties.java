package fu.fa26.sep490.g58.otcms_be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.google")
public record GoogleOidcProperties(String issuer, String audience, String clientSecret,
                                   String tokenUri, String jwkSetUri, int connectTimeoutMs, int readTimeoutMs) {
    public GoogleOidcProperties {
        connectTimeoutMs = connectTimeoutMs > 0 ? connectTimeoutMs : 5000;
        readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : 10000;
        issuer = issuer == null ? "https://accounts.google.com" : issuer;
        tokenUri = tokenUri == null ? "https://oauth2.googleapis.com/token" : tokenUri;
        jwkSetUri = jwkSetUri == null ? "https://www.googleapis.com/oauth2/v3/certs" : jwkSetUri;
    }
    public void requireConfigured() {
        if (audience == null || audience.isBlank() || clientSecret == null || clientSecret.isBlank())
            throw new IllegalStateException("Google OIDC client configuration is not available");
    }
}
