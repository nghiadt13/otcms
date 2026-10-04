package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.port.GoogleOidcPort;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class T007GoogleAuthenticationServiceTest {
    @Test void verifiedIdentityLinksOnlyToActivePatientAndIssuesSession() {
        var oidc = mock(GoogleOidcPort.class); var accounts = mock(AccountRepository.class);
        var links = mock(ExternalIdentityRepository.class); var sessions = mock(AuthSessionService.class);
        var profiles = mock(PatientProfileRepository.class); var consents = mock(ConsentEvidenceRepository.class);
        var config = new fu.fa26.sep490.g58.otcms_be.config.AuthProperties(null, null, null, null, null, null, "v1", "v1");
        var account = account(AccountStatus.ACTIVE); var issued = new AuthSessionService.IssuedSession(null, "refresh");
        when(oidc.exchange("code", "verifier", "https://patient.example/callback"))
                .thenReturn(new GoogleOidcPort.VerifiedGoogleIdentity("google-sub", "patient@example.com", true));
        when(links.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub")).thenReturn(Optional.empty());
        when(accounts.findByNormalizedEmail("patient@example.com")).thenReturn(Optional.of(account));
        when(sessions.create(eq(account), any(), any())).thenReturn(issued);
        var result = new GoogleAuthenticationService(oidc, accounts, links, sessions, profiles, consents, config)
                .exchange("code", "verifier", "https://patient.example/callback", "198.51.100.10", "test-agent");
        assertSame(issued, result); verify(links).saveAndFlush(any(ExternalIdentity.class)); verify(sessions).create(account, "198.51.100.10", "test-agent");
    }

    @Test void missingMatchingAccountFailsWithOnboardingRequiredWithoutLink() {
        var oidc = mock(GoogleOidcPort.class); var accounts = mock(AccountRepository.class);
        var links = mock(ExternalIdentityRepository.class); var sessions = mock(AuthSessionService.class);
        var profiles = mock(PatientProfileRepository.class); var consents = mock(ConsentEvidenceRepository.class);
        var config = new fu.fa26.sep490.g58.otcms_be.config.AuthProperties(null, null, null, null, null, null, "v1", "v1");
        when(oidc.exchange(any(), any(), any())).thenReturn(new GoogleOidcPort.VerifiedGoogleIdentity("sub", "new@example.com", true));
        when(links.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(accounts.findByNormalizedEmail("new@example.com")).thenReturn(Optional.empty());
        var service = new GoogleAuthenticationService(oidc, accounts, links, sessions, profiles, consents, config);
        var ex = assertThrows(GoogleAuthenticationException.class, () -> service.exchange("c", "v", "r", "ip", "ua"));
        assertEquals("GOOGLE_ONBOARDING_REQUIRED", ex.getMessage()); verifyNoInteractions(sessions); verify(links, never()).saveAndFlush(any());
    }

    @Test void unverifiedEmailFailsBeforePersistence() {
        var oidc = mock(GoogleOidcPort.class); var accounts = mock(AccountRepository.class);
        var links = mock(ExternalIdentityRepository.class); var sessions = mock(AuthSessionService.class);
        var profiles = mock(PatientProfileRepository.class); var consents = mock(ConsentEvidenceRepository.class);
        var config = new fu.fa26.sep490.g58.otcms_be.config.AuthProperties(null, null, null, null, null, null, "v1", "v1");
        when(oidc.exchange(any(), any(), any())).thenReturn(new GoogleOidcPort.VerifiedGoogleIdentity("sub", "x@example.com", false));
        var ex = assertThrows(GoogleAuthenticationException.class, () -> new GoogleAuthenticationService(oidc, accounts, links, sessions, profiles, consents, config).exchange("c", "v", "r", "ip", "ua"));
        assertEquals("AUTHENTICATION_FAILED", ex.getMessage()); verifyNoInteractions(accounts, links, sessions);
    }

    private static Account account(AccountStatus status) { var a = new Account(); a.setId(UUID.randomUUID()); a.setAccountType(AccountType.PATIENT); a.setStatus(status); a.setNormalizedEmail("patient@example.com"); return a; }
}
