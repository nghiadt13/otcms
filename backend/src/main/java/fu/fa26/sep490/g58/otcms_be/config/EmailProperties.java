package fu.fa26.sep490.g58.otcms_be.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int writeTimeoutMs;
    private final int maxAttempts;
    @ConstructorBinding
    public EmailProperties(boolean enabled, String host, int port, String username, String password, String from, int connectTimeoutMs, int readTimeoutMs, int writeTimeoutMs, int maxAttempts) {
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
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        this.maxAttempts = maxAttempts;
    }
    public boolean getEnabled() { return enabled; }
    public boolean isEnabled() { return enabled; }
    public boolean enabled() { return enabled; }
    public String getHost() { return host; }
    public String host() { return host; }
    public int getPort() { return port; }
    public int port() { return port; }
    public String getUsername() { return username; }
    public String username() { return username; }
    public String getPassword() { return password; }
    public String password() { return password; }
    public String getFrom() { return from; }
    public String from() { return from; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public int connectTimeoutMs() { return connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public int readTimeoutMs() { return readTimeoutMs; }
    public int getWriteTimeoutMs() { return writeTimeoutMs; }
    public int writeTimeoutMs() { return writeTimeoutMs; }
    public int getMaxAttempts() { return maxAttempts; }
    public int maxAttempts() { return maxAttempts; }
}
