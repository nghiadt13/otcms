package fu.fa26.sep490.g58.otcms_be.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.List;

/** Typed deployment binding for JWT, browser cookies and exact-origin CORS. */
@ConfigurationProperties(prefix = "app.auth.session-security")
public class SessionSecurityProperties {
    private final String jwtSecret;
    private final long accessTokenSeconds;
    private final long refreshTokenSeconds;
    private final String cookieSameSite;
    private final boolean cookieSecure;
    private final List<String> patientOrigins;
    private final List<String> staffOrigins;
    @ConstructorBinding
    public SessionSecurityProperties(String jwtSecret, long accessTokenSeconds, long refreshTokenSeconds, String cookieSameSite, boolean cookieSecure, List<String> patientOrigins, List<String> staffOrigins) {
        accessTokenSeconds = accessTokenSeconds > 0 ? accessTokenSeconds : 900;
        refreshTokenSeconds = refreshTokenSeconds > 0 ? refreshTokenSeconds : 604800;
        cookieSameSite = cookieSameSite == null || cookieSameSite.isBlank() ? "Strict" : cookieSameSite;
        if (!List.of("Strict", "Lax", "None").contains(cookieSameSite)) {
            throw new IllegalArgumentException("cookieSameSite must be Strict, Lax or None");
        }
        if ("None".equals(cookieSameSite) && !cookieSecure) {
            throw new IllegalArgumentException("SameSite=None requires Secure cookies");
        }
        patientOrigins = exactOrigins(patientOrigins);
        staffOrigins = exactOrigins(staffOrigins);
        this.jwtSecret = jwtSecret;
        this.accessTokenSeconds = accessTokenSeconds;
        this.refreshTokenSeconds = refreshTokenSeconds;
        this.cookieSameSite = cookieSameSite;
        this.cookieSecure = cookieSecure;
        this.patientOrigins = patientOrigins;
        this.staffOrigins = staffOrigins;
    }
    public String getJwtSecret() { return jwtSecret; }
    public String jwtSecret() { return jwtSecret; }
    public long getAccessTokenSeconds() { return accessTokenSeconds; }
    public long accessTokenSeconds() { return accessTokenSeconds; }
    public long getRefreshTokenSeconds() { return refreshTokenSeconds; }
    public long refreshTokenSeconds() { return refreshTokenSeconds; }
    public String getCookieSameSite() { return cookieSameSite; }
    public String cookieSameSite() { return cookieSameSite; }
    public boolean getCookieSecure() { return cookieSecure; }
    public boolean isCookieSecure() { return cookieSecure; }
    public boolean cookieSecure() { return cookieSecure; }
    public List<String> getPatientOrigins() { return patientOrigins; }
    public List<String> patientOrigins() { return patientOrigins; }
    public List<String> getStaffOrigins() { return staffOrigins; }
    public List<String> staffOrigins() { return staffOrigins; }
    public byte[] requiredJwtSecret() {
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.auth.session-security.jwt-secret must contain at least 32 bytes");
        }
        return jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<String> exactOrigins(List<String> values) {
        if (values == null) return List.of();
        for (String value : values) {
            if (value == null || value.isBlank() || value.contains("*")) {
                throw new IllegalArgumentException("CORS origins must be non-empty exact origins");
            }
        }
        return List.copyOf(values);
    }
}
