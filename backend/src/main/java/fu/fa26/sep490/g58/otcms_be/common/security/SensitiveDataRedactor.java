package fu.fa26.sep490.g58.otcms_be.common.security;

import java.util.regex.Pattern;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Redacts credential-like values before they reach logs or client-facing errors. */
public final class SensitiveDataRedactor {
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(\\\"?(?:accessToken|refreshToken|passwordHash|password|passcode|token|secret|otp|verificationCode|authorization)\\\"?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|[^,;}\\s]+)",
            Pattern.MULTILINE);
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(\\bAuthorization\\s*[:=]\\s*Bearer\\s+)([^,;}\\s\\\"]+)");
    private static final Pattern AUDIT_SECRET_KEY = Pattern.compile(
            "(?i).*(password|passcode|token|secret|otp|verificationcode|authorization|apikey|credential|nationalid|citizenid|identitynumber|cccd).*");

    private SensitiveDataRedactor() { }

    public static String redact(String value) {
        if (value == null) return null;
        String redacted = BEARER.matcher(value).replaceAll("$1[REDACTED]");
        return SECRET.matcher(redacted).replaceAll("$1[REDACTED]");
    }

    /** Redacts a Jackson-normalized JSON value before serialization, preserving valid JSON. */
    public static Object redactJson(Object value) {
        if (value instanceof Map<?, ?> fields) {
            Map<String, Object> result = new LinkedHashMap<>();
            fields.forEach((key, item) -> {
                String name = key.toString();
                boolean sensitive = AUDIT_SECRET_KEY.matcher(name.replaceAll("[^A-Za-z0-9]", "")).matches();
                result.put(name, sensitive ? "[REDACTED]" : redactJson(item));
            });
            return result;
        }
        if (value instanceof List<?> items) return items.stream().map(SensitiveDataRedactor::redactJson).toList();
        return value instanceof String text ? redact(text) : value;
    }
}
