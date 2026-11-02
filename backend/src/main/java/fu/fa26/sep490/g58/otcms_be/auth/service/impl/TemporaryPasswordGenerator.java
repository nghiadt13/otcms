package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class TemporaryPasswordGenerator {
    private final SecureRandom random = new SecureRandom();

    /** 256 random bits produce 43 ASCII characters, within the existing 12..64/72-byte policy. */
    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
