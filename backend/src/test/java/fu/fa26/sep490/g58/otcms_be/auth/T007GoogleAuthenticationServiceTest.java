package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.GoogleOidcService;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.GoogleAuthenticationServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest @ValueSource(strings = {"original", "reused", "unverified", "mismatch"})
    void linkedIdentityUsesEmailAtLinkAfterAccountEmailChange(String mode) {
        var oidc = mock(GoogleOidcService.class); var accounts = mock(AccountRepository.class);
        var links = mock(ExternalIdentityRepository.class); var sessions = mock(AuthSessionService.class);
        var profiles = mock(PatientProfileRepository.class); var consents = mock(ConsentEvidenceRepository.class);
        var config = new fu.fa26.sep490.g58.otcms_be.config.AuthProperties(null, null, null, null, null, null, "v1", "v1");
        var service = new GoogleAuthenticationServiceImpl(oidc, accounts, links, sessions, profiles, consents, config);
        var owner = account(AccountStatus.ACTIVE);
        String originalEmail = owner.getNormalizedEmail();
        var issued = new AuthSessionService.IssuedSession(null, "refresh");
        when(oidc.exchange(any(), any(), any()))
                .thenReturn(new GoogleOidcService.VerifiedGoogleIdentity("google-sub", originalEmail, true));
        when(accounts.findByNormalizedEmail(originalEmail)).thenReturn(Optional.of(owner));
        when(sessions.create(eq(owner.getId()), any(), any())).thenReturn(issued);
        when(links.saveAndFlush(any())).thenAnswer(invocation -> {
            ExternalIdentityJpaEntity link = invocation.getArgument(0);
            when(links.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                    .thenReturn(Optional.of(link));
            return link;
        });
        assertSame(issued, service.exchange("c", "v", "r", "ip", "ua"));
        // The OTP workflow is exercised against PostgreSQL; here model its resulting account email.
        owner.setNormalizedEmail("changed@example.com");
        var other = account(AccountStatus.ACTIVE);
        if (mode.equals("reused")) when(accounts.findByNormalizedEmail(originalEmail)).thenReturn(Optional.of(other));
        when(oidc.exchange(any(), any(), any())).thenReturn(new GoogleOidcService.VerifiedGoogleIdentity(
                "google-sub", mode.equals("mismatch") ? owner.getNormalizedEmail() : originalEmail,
                !mode.equals("unverified")));
        if (mode.equals("unverified") || mode.equals("mismatch")) {
            var ex = assertThrows(GoogleAuthenticationException.class, () -> service.exchange("c", "v", "r", "ip", "ua"));
            assertEquals(mode.equals("unverified") ? "AUTHENTICATION_FAILED" : "IDENTITY_LINK_CONFLICT", ex.getMessage());
            verify(sessions).create(owner.getId(), "ip", "ua");
        } else {
            assertSame(issued, service.exchange("c", "v", "r", "ip", "ua"));
            verify(sessions, times(2)).create(owner.getId(), "ip", "ua");
        }
        verify(accounts).findByNormalizedEmail(originalEmail);
        verifyNoMoreInteractions(accounts, sessions);
        var link = links.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "google-sub").orElseThrow();
        assertSame(owner, link.getAccount());
        assertEquals(originalEmail, link.getNormalizedEmailAtLink());
        verify(links).saveAndFlush(any());
        verifyNoInteractions(profiles, consents);
    }

    private static AccountJpaEntity account(AccountStatus status) { var a = new AccountJpaEntity(); a.setId(UUID.randomUUID()); a.setAccountType(AccountType.PATIENT); a.setStatus(status); a.setNormalizedEmail("patient@example.com"); return a; }
}
