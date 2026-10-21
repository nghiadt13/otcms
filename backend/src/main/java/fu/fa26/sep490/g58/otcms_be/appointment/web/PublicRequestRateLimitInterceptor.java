package fu.fa26.sep490.g58.otcms_be.appointment.web;

import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class PublicRequestRateLimitInterceptor implements HandlerInterceptor {
    private static final String PUBLIC_CATALOG_PATH = "/api/v1/clinic-services";
    private static final String PUBLIC_AVAILABILITY_PATH = "/api/v1/appointments/availability";

    private final TrustedProxyIpResolver ipResolver;
    private final PublicRequestRateLimiter limiter;
    private final Clock clock;

    public PublicRequestRateLimitInterceptor(TrustedProxyIpResolver ipResolver,
            PublicRequestRateLimiter limiter, Clock clock) {
        this.ipResolver = ipResolver;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod()) && !"HEAD".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        PublicRequestRateLimiter.Bucket bucket = switch (path) {
            case PUBLIC_CATALOG_PATH -> PublicRequestRateLimiter.Bucket.PUBLIC_CATALOG;
            case PUBLIC_AVAILABILITY_PATH -> PublicRequestRateLimiter.Bucket.PUBLIC_AVAILABILITY;
            default -> null;
        };
        if (bucket == null) return true;

        PublicRequestRateLimiter.Decision decision = limiter.acquire(bucket, ipResolver.resolve(request));
        if (decision.allowed()) return true;

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String correlationId = escapeJson(CorrelationIdFilter.current(request));
        response.getWriter().write("{\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\","
                + "\"message\":\"Too many public requests\",\"timestamp\":\""
                + Instant.now(clock) + "\",\"correlationId\":\"" + correlationId + "\"}}");
        return false;
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }
}
