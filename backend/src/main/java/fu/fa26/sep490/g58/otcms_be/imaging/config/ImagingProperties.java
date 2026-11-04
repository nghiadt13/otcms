package fu.fa26.sep490.g58.otcms_be.imaging.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Imaging runtime settings. Capacity values stay unset until T026 evidence supplies them. */
@ConfigurationProperties(prefix = "otcms.imaging")
public class ImagingProperties {
    private boolean enabled;
    private long maxUploadBytes;
    private int maxUploadFiles;
    private int maxConcurrentUploads;
    private URI orthancBaseUri;
    private Duration orthancConnectTimeout;
    private Duration orthancResponseTimeout;
    private int confirmationMaxAttempts = 8;
    private Duration confirmationMaxDelay = Duration.ofMinutes(5);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getMaxUploadBytes() { return maxUploadBytes; }
    public void setMaxUploadBytes(long maxUploadBytes) { this.maxUploadBytes = maxUploadBytes; }
    public int getMaxUploadFiles() { return maxUploadFiles; }
    public void setMaxUploadFiles(int maxUploadFiles) { this.maxUploadFiles = maxUploadFiles; }
    public int getMaxConcurrentUploads() { return maxConcurrentUploads; }
    public void setMaxConcurrentUploads(int maxConcurrentUploads) { this.maxConcurrentUploads = maxConcurrentUploads; }
    public URI getOrthancBaseUri() { return orthancBaseUri; }
    public void setOrthancBaseUri(URI orthancBaseUri) { this.orthancBaseUri = orthancBaseUri; }
    public Duration getOrthancConnectTimeout() { return orthancConnectTimeout; }
    public void setOrthancConnectTimeout(Duration orthancConnectTimeout) {
        this.orthancConnectTimeout = orthancConnectTimeout;
    }
    public Duration getOrthancResponseTimeout() { return orthancResponseTimeout; }
    public void setOrthancResponseTimeout(Duration orthancResponseTimeout) {
        this.orthancResponseTimeout = orthancResponseTimeout;
    }
    public int getConfirmationMaxAttempts() { return confirmationMaxAttempts; }
    public void setConfirmationMaxAttempts(int confirmationMaxAttempts) {
        this.confirmationMaxAttempts = confirmationMaxAttempts;
    }
    public Duration getConfirmationMaxDelay() { return confirmationMaxDelay; }
    public void setConfirmationMaxDelay(Duration confirmationMaxDelay) {
        this.confirmationMaxDelay = confirmationMaxDelay;
    }

    /** Call when enabling imaging operations; absent workload values must never become an implicit cap. */
    public void validateEnabledSettings() {
        if (!enabled) return;
        if (maxUploadBytes < 1 || maxUploadFiles < 1 || maxConcurrentUploads < 1) {
            throw new IllegalStateException("Imaging upload capacity settings must be explicitly configured");
        }
        if (orthancBaseUri == null || !isHttpUri(orthancBaseUri)) {
            throw new IllegalStateException("A private Orthanc HTTP(S) base URI must be configured");
        }
        if (!isPositive(orthancConnectTimeout) || !isPositive(orthancResponseTimeout)) {
            throw new IllegalStateException("Orthanc timeouts must be explicitly configured and positive");
        }
        if (confirmationMaxAttempts < 1 || confirmationMaxAttempts > 8
                || confirmationMaxDelay == null || confirmationMaxDelay.isNegative()
                || confirmationMaxDelay.isZero() || confirmationMaxDelay.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalStateException("Confirmation retry settings exceed the approved T010 bound");
        }
    }

    private static boolean isHttpUri(URI uri) {
        String scheme = uri.getScheme();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && uri.getHost() != null && uri.getUserInfo() == null
                && uri.getQuery() == null && uri.getFragment() == null;
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
