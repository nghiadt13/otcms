package fu.fa26.sep490.g58.otcms_be.auth.port;

import fu.fa26.sep490.g58.otcms_be.auth.service.GoogleAuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/** Fails closed until deployment supplies the configured Google OIDC adapter. */
@Component
@Profile("google-fallback")
public class UnavailableGoogleOidcAdapter implements GoogleOidcPort {
    @Override
    public VerifiedGoogleIdentity exchange(String authorizationCode, String codeVerifier, String redirectUri) {
        throw new GoogleAuthenticationException("AUTHENTICATION_FAILED");
    }
}
