package fu.fa26.sep490.g58.otcms_be.auth.service;


import fu.fa26.sep490.g58.otcms_be.auth.exception.GoogleAuthenticationException;

/** Backend boundary for Google Authorization Code + PKCE exchange. */
    public interface GoogleOidcService {
    VerifiedGoogleIdentity exchange(String authorizationCode, String codeVerifier, String redirectUri)
            throws GoogleAuthenticationException;

        static class VerifiedGoogleIdentity {
    private final String subject;
    private final String normalizedEmail;
    private final boolean emailVerified;
    public VerifiedGoogleIdentity(String subject, String normalizedEmail, boolean emailVerified) {
        this.subject = subject;
        this.normalizedEmail = normalizedEmail;
        this.emailVerified = emailVerified;
    }
    public String getSubject() { return subject; }
    public String subject() { return subject; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String normalizedEmail() { return normalizedEmail; }
    public boolean getEmailVerified() { return emailVerified; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean emailVerified() { return emailVerified; }
}
}
