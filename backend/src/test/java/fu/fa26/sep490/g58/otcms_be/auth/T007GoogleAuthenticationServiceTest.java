package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.GoogleOidcService;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.GoogleAuthenticationServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.exception.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class T007GoogleAuthenticationServiceTest {
    @Test void verifiedIdentityLinksOnlyToActivePatientAndIssuesSession() {
        var oidc = mock(GoogleOidcService.class); var accounts = mock(AccountRepository.class);
        var links = mock(ExternalIdentityRepository.class); var sessions = mock(AuthSessionService.class);
        var profiles = mock(PatientProfileRepository.class); var consents = mock(ConsentEvidenceRepository.class);
        var config = new fu.fa26.sep490.g58.otcms_be.config.AuthProperties(null, null, null, null, null, null, "v1", "v1");
        var account = account(AccountStatus.ACTIVE); var issued = new AuthSessionService.IssuedSession(null, "refresh");
        when(oidc.exchange("code", "verifier", "https://patient.example/callback"))
                .thenReturn(new GoogleOidcService.VerifiedGoogleIdentity("google-sub", "patient@example.com", true));
        when(links.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub")).thenReturn(Optional.empty());
        when(accounts.findByNormalizedEmail("patient@example.com")).thenReturn(Optional.of(account));
        when(sessions.create(eq(account.getId()), any(), any())).thenReturn(issued);
        var result = new GoogleAuthenticationServiceImpl(oidc, accounts, links, sessions, profiles, consents, config)
                .exchange("code", "verifier", "https://patient.example/callback", "198.51.100.10", "test-agent");
        assertSame(issued, result); verify(links).saveAndFlush(any(ExternalIdentityJpaEntity.class)); verify(sessions).create(account.getId(), "198.51.100.10", "test-agent");
    }

    @Test void missingMatchingAccountFailsWithOnboardingRequiredWithoutLink() {
        var oidc = mock(GoogleOidcService.class); var accounts = mock(AccountRepository.class);
        var links = mock(ExternalIdentityRepository.class); var sessions = mock(AuthSessionService.class);
        var profiles = mock(PatientProfileRepository.class); var consents = mock(ConsentEvidenceRepository.class);
        var config = new fu.fa26.sep490.g58.otcms_be.config.AuthProperties(null, null, null, null, null, null, "v1", "v1");
        when(oidc.exchange(any(), any(), any())).thenReturn(new GoogleOidcService.VerifiedGoogleIdentity("sub", "new@example.com", true));
        when(links.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(accounts.findByNormalizedEmail("new@example.com")).thenReturn(Optional.empty());
        var service = new GoogleAuthenticationServiceImpl(oidc, accounts, links, sessions, profiles, consents, config);
        var ex = assertThrows(GoogleAuthenticationException.class, () -> service.exchange("c", "v", "r", "ip", "ua"));
        assertEquals("GOOGLE_ONBOARDING_REQUIRED", ex.getMessage()); verifyNoInteractions(sessions); verify(links, never()).saveAndFlush(any());
    }

    @Test void unverifiedEmailFailsBeforePersistence() {
        var oidc = mock(GoogleOidcService.class); var accounts = mock(AccountRepository.class);
        var links = mock(ExternalIdentityRepository.class); var sessions = mock(AuthSessionService.class);
        var profiles = mock(PatientProfileRepository.class); var consents = mock(ConsentEvidenceRepository.class);
        var config = new fu.fa26.sep490.g58.otcms_be.config.AuthProperties(null, null, null, null, null, null, "v1", "v1");
        when(oidc.exchange(any(), any(), any())).thenReturn(new GoogleOidcService.VerifiedGoogleIdentity("sub", "x@example.com", false));
        var ex = assertThrows(GoogleAuthenticationException.class, () -> new GoogleAuthenticationServiceImpl(oidc, accounts, links, sessions, profiles, consents, config).exchange("c", "v", "r", "ip", "ua"));
        assertEquals("AUTHENTICATION_FAILED", ex.getMessage()); verifyNoInteractions(accounts, links, sessions);
    }

    private static AccountJpaEntity account(AccountStatus status) { var a = new AccountJpaEntity(); a.setId(UUID.randomUUID()); a.setAccountType(AccountType.PATIENT); a.setStatus(status); a.setNormalizedEmail("patient@example.com"); return a; }
}
