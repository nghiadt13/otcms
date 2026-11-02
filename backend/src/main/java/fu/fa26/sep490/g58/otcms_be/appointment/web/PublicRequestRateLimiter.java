package fu.fa26.sep490.g58.otcms_be.appointment.web;


import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Process-local fixed-window limiter. Deploy one application instance unless a shared store is reviewed. */
@Component
public class PublicRequestRateLimiter {
    private static final int CLEANUP_INTERVAL_REQUESTS = 16;
    private static final int MAX_CLEANUP_ENTRIES = 64;

    private final int requestLimit;
    private final int windowSeconds;
    private final Clock clock;
    private final ConcurrentHashMap<BucketKey, WindowState> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestsSeen = new AtomicLong();
    private Iterator<Map.Entry<BucketKey, WindowState>> cleanupIterator;

    public PublicRequestRateLimiter(AppointmentBookingProperties properties, Clock clock) {
        this.requestLimit = properties.publicRateLimit().requests();
        this.windowSeconds = properties.publicRateLimit().windowSeconds();
        this.clock = clock;
    }

    public Decision acquire(Bucket bucket, String clientIp) {
        Instant now = Instant.now(clock);
        if (requestsSeen.incrementAndGet() % CLEANUP_INTERVAL_REQUESTS == 0) cleanup(now);

        long windowStart = Math.floorDiv(now.getEpochSecond(), windowSeconds) * windowSeconds;
        BucketKey key = new BucketKey(bucket, clientIp);
        Holder holder = new Holder();
        windows.compute(key, (ignored, current) -> {
            if (current == null || current.windowStartEpochSecond != windowStart) {
                WindowState fresh = new WindowState(windowStart, 1);
                holder.allowed = true;
                return fresh;
            }
            if (current.count < requestLimit) {
                WindowState incremented = new WindowState(current.windowStartEpochSecond, current.count + 1);
                holder.allowed = true;
                return incremented;
            }
            holder.allowed = false;
            return current;
        });

        if (holder.allowed) return new Decision(true, 0);
        Instant reset = Instant.ofEpochSecond(windowStart + windowSeconds);
        Duration remaining = Duration.between(now, reset);
        long retryAfter = Math.max(1, remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0));
        return new Decision(false, retryAfter);
    }

    synchronized int cleanup(Instant now) {
        int inspected = 0;
        while (inspected < MAX_CLEANUP_ENTRIES) {
            if (cleanupIterator == null || !cleanupIterator.hasNext()) {
                cleanupIterator = windows.entrySet().iterator();
                if (!cleanupIterator.hasNext()) break;
            }
            Map.Entry<BucketKey, WindowState> entry = cleanupIterator.next();
            inspected++;
            WindowState state = entry.getValue();
            long expiry = state.windowStartEpochSecond + windowSeconds;
            if (now.getEpochSecond() >= expiry) windows.remove(entry.getKey(), state);
        }
        return inspected;
    }

    int trackedBucketCount() { return windows.size(); }

    public enum Bucket { PUBLIC_CATALOG, PUBLIC_AVAILABILITY }

        public static class Decision {
    private final boolean allowed;
    private final long retryAfterSeconds;
    public Decision(boolean allowed, long retryAfterSeconds) {
        this.allowed = allowed;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public boolean getAllowed() { return allowed; }
    public boolean isAllowed() { return allowed; }
    public boolean allowed() { return allowed; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}

        private static class BucketKey {
    private final Bucket bucket;
    private final String clientIp;
    public BucketKey(Bucket bucket, String clientIp) {
        this.bucket = bucket;
        this.clientIp = clientIp;
    }
    public Bucket getBucket() { return bucket; }
    public Bucket bucket() { return bucket; }
    public String getClientIp() { return clientIp; }
    public String clientIp() { return clientIp; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof BucketKey key
                && bucket == key.bucket && Objects.equals(clientIp, key.clientIp);
    }
    @Override public int hashCode() { return Objects.hash(bucket, clientIp); }
}
        private static class WindowState {
    private final long windowStartEpochSecond;
    private final int count;
    public WindowState(long windowStartEpochSecond, int count) {
        this.windowStartEpochSecond = windowStartEpochSecond;
        this.count = count;
    }
    public long getWindowStartEpochSecond() { return windowStartEpochSecond; }
    public long windowStartEpochSecond() { return windowStartEpochSecond; }
    public int getCount() { return count; }
    public int count() { return count; }
}

    private static final class Holder {
        private boolean allowed;
    }
}
