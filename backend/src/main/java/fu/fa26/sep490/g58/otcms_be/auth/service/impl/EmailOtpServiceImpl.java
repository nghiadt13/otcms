package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.EmailOtpService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class EmailOtpServiceImpl implements EmailOtpService {
    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    public String generate() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
    public String hash(String code) { return encoder.encode(code); }
    public boolean matches(String code, String hash) { return code != null && encoder.matches(code, hash); }
    public Instant expiresAt(Instant createdAt) { return createdAt.plusSeconds(600); }
}
