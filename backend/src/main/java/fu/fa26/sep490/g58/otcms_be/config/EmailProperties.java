package fu.fa26.sep490.g58.otcms_be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(boolean enabled, String host, int port, String username, String password,
                              String from, int connectTimeoutMs, int readTimeoutMs, int writeTimeoutMs,
                              int maxAttempts) {
    @ConstructorBinding
    public EmailProperties {
        host = host == null || host.isBlank() ? "smtp.gmail.com" : host;
        port = port < 1 ? 587 : port;
        connectTimeoutMs = connectTimeoutMs < 1 ? 5000 : connectTimeoutMs;
        readTimeoutMs = readTimeoutMs < 1 ? 10000 : readTimeoutMs;
        writeTimeoutMs = writeTimeoutMs < 1 ? 10000 : writeTimeoutMs;
        maxAttempts = maxAttempts < 1 ? 2 : Math.min(maxAttempts, 2);
        if (enabled && (username == null || username.isBlank() || password == null || password.isBlank()
                || from == null || from.isBlank())) {
            throw new IllegalArgumentException("Enabled email delivery requires username, password and from");
        }
        from = from == null || from.isBlank() ? username : from;
    }
}
