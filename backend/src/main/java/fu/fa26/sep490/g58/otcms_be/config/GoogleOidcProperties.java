package fu.fa26.sep490.g58.otcms_be.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.auth.google")
public class GoogleOidcProperties {
    private final String issuer;
    private final String audience;
    private final String clientSecret;
    private final String tokenUri;
    private final String jwkSetUri;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    @ConstructorBinding
    public GoogleOidcProperties(String issuer, String audience, String clientSecret, String tokenUri, String jwkSetUri, int connectTimeoutMs, int readTimeoutMs) {
        connectTimeoutMs = connectTimeoutMs > 0 ? connectTimeoutMs : 5000;
        readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : 10000;
        issuer = issuer == null ? "https://accounts.google.com" : issuer;
        tokenUri = tokenUri == null ? "https://oauth2.googleapis.com/token" : tokenUri;
        jwkSetUri = jwkSetUri == null ? "https://www.googleapis.com/oauth2/v3/certs" : jwkSetUri;
        this.issuer = issuer;
        this.audience = audience;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.jwkSetUri = jwkSetUri;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }
    public String getIssuer() { return issuer; }
    public String issuer() { return issuer; }
    public String getAudience() { return audience; }
    public String audience() { return audience; }
    public String getClientSecret() { return clientSecret; }
    public String clientSecret() { return clientSecret; }
    public String getTokenUri() { return tokenUri; }
    public String tokenUri() { return tokenUri; }
    public String getJwkSetUri() { return jwkSetUri; }
    public String jwkSetUri() { return jwkSetUri; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public int connectTimeoutMs() { return connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public int readTimeoutMs() { return readTimeoutMs; }
    public void requireConfigured() {
        if (audience == null || audience.isBlank() || clientSecret == null || clientSecret.isBlank())
            throw new IllegalStateException("Google OIDC client configuration is not available");
    }
}
