package fu.fa26.sep490.g58.otcms_be.common.security;

import java.util.regex.Pattern;

/** Redacts credential-like values before they reach logs or client-facing errors. */
public final class SensitiveDataRedactor {
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(\\\"?(?:accessToken|refreshToken|passwordHash|password|passcode|token|secret|otp|verificationCode|authorization)\\\"?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|[^,;}\\s]+)",
            Pattern.MULTILINE);
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(\\bAuthorization\\s*[:=]\\s*Bearer\\s+)([^,;}\\s\\\"]+)");

    private SensitiveDataRedactor() { }

    public static String redact(String value) {
        if (value == null) return null;
        String redacted = BEARER.matcher(value).replaceAll("$1[REDACTED]");
        return SECRET.matcher(redacted).replaceAll("$1[REDACTED]");
    }
}
