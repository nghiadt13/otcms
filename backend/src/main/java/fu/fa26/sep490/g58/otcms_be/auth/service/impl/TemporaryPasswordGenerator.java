package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class TemporaryPasswordGenerator {
    private final SecureRandom random;

    public TemporaryPasswordGenerator() { this(new SecureRandom()); }

    TemporaryPasswordGenerator(SecureRandom random) { this.random = random; }

    /** Keep 256 random bits per candidate; reject candidates lacking BR-04's letter/digit. */
    public String generate() {
        byte[] bytes = new byte[32];
        String candidate;
        do {
            random.nextBytes(bytes);
            candidate = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (!PasswordPolicy.isValid(candidate));
        return candidate;
    }
}
