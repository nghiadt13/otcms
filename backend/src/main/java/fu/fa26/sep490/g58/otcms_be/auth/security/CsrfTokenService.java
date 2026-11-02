package fu.fa26.sep490.g58.otcms_be.auth.security;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CsrfTokenService {
    private final SecureRandom random = new SecureRandom();
    public String issue() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public boolean valid(String cookie, String header) { return cookie != null && header != null && java.security.MessageDigest.isEqual(cookie.getBytes(), header.getBytes()); }
}
