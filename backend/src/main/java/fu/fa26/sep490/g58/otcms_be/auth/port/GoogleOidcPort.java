package fu.fa26.sep490.g58.otcms_be.auth.port;

import fu.fa26.sep490.g58.otcms_be.auth.service.GoogleAuthenticationException;

/** Backend boundary for Google Authorization Code + PKCE exchange. */
public interface GoogleOidcPort {
    VerifiedGoogleIdentity exchange(String authorizationCode, String codeVerifier, String redirectUri)
            throws GoogleAuthenticationException;

    record VerifiedGoogleIdentity(String subject, String normalizedEmail, boolean emailVerified) {}
}
