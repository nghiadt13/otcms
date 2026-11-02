package fu.fa26.sep490.g58.otcms_be.auth.service;

import java.time.Instant;

/** OTP primitives only; each business flow owns its challenge lifecycle and recipient. */
public interface EmailOtpService {
    int MAX_FAILED_ATTEMPTS = 5;
    String generate();
    String hash(String code);
    boolean matches(String code, String hash);
    Instant expiresAt(Instant createdAt);
}
