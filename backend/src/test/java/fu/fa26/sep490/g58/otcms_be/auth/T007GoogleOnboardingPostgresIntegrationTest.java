package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.GoogleExchangeRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.GoogleOidcService;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.exception.*;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.auth.session-security.jwt-secret=t007-postgres-secret-32-bytes-minimum",
        "app.auth.terms-version=v1", "app.auth.privacy-version=v1"
})
@LocalPostgresTest
class T007GoogleOnboardingPostgresIntegrationTest {
    @Autowired GoogleAuthenticationService service;
    @Autowired AccountRepository accounts;
    @Autowired ExternalIdentityRepository identities;
    @MockitoBean PatientProfileRepository profiles;
    @MockitoBean ConsentEvidenceRepository consents;
    @Autowired AuthSessionRepository sessions;
    @MockitoBean GoogleOidcService provider;
    private final String runId = UUID.randomUUID().toString();

    @AfterEach void cleanup() {
        for (String email : new String[]{fixtureEmail("owner"), fixtureEmail("new"), fixtureEmail("attacker"),
                fixtureEmail("rollback"), fixtureEmail("consent-rollback")}) {
            accounts.findByNormalizedEmail(email).ifPresent(account -> {
                sessions.deleteAll(sessions.findAllByAccountId(account.getId()));
                identities.deleteAll(identities.findAllByAccount_Id(account.getId()));
                accounts.delete(account);
            });
        }
    }

    @Test void missingOnboardingDataDoesNotCreateAccountOrIdentity() {
        String email = fixtureEmail("new");
        when(provider.exchange(any(), any(), any())).thenReturn(identity("new-sub-" + runId, email));
        assertThrows(GoogleAuthenticationException.class, () -> service.exchange(new GoogleExchangeRequest("c", "v", "r"), "127.0.0.1", "test"));
        assertTrue(accounts.findByNormalizedEmail(email).isEmpty());
        assertTrue(identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "new-sub-" + runId).isEmpty());
    }

    @Test void providerSubjectLinkedToDifferentEmailRollsBackWithoutNewLinkage() {
        String ownerEmail = fixtureEmail("owner");
        String attackerEmail = fixtureEmail("attacker");
        String subject = "shared-sub-" + runId;
        AccountJpaEntity owner = account(ownerEmail);
        ExternalIdentityJpaEntity existing = new ExternalIdentityJpaEntity(); existing.setAccount(owner); existing.setProvider(IdentityProvider.GOOGLE); existing.setProviderSubject(subject); existing.setNormalizedEmailAtLink(owner.getNormalizedEmail()); existing.setLinkedAt(java.time.Instant.now()); identities.saveAndFlush(existing);
        when(provider.exchange(any(), any(), any())).thenReturn(identity(subject, attackerEmail));
        assertThrows(GoogleAuthenticationException.class, () -> service.exchange(onboarding(attackerEmail), "127.0.0.1", "test"));
        assertTrue(accounts.findByNormalizedEmail(attackerEmail).isEmpty());
        assertEquals(owner.getId(), identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, subject).orElseThrow().getAccount().getId());
    }

    @Test void profilePersistenceFailureRollsBackAccountAndLinkage() {
        String email = fixtureEmail("rollback");
        String subject = "rollback-sub-" + runId;
        when(provider.exchange(any(), any(), any())).thenReturn(identity(subject, email));
        doThrow(new IllegalStateException("simulated profile persistence failure")).when(profiles).save(any(PatientProfileJpaEntity.class));
        var request = onboarding(email);
        assertThrows(RuntimeException.class, () -> service.exchange(request, "127.0.0.1", "test"));
        assertTrue(accounts.findByNormalizedEmail(email).isEmpty());
        assertTrue(identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, subject).isEmpty());
    }

    @Test void consentPersistenceFailureRollsBackAccountAndLinkage() {
        String email = fixtureEmail("consent-rollback");
        String subject = "consent-rollback-sub-" + runId;
        when(provider.exchange(any(), any(), any())).thenReturn(identity(subject, email));
        doThrow(new IllegalStateException("simulated consent persistence failure")).when(consents).save(any(ConsentEvidenceJpaEntity.class));
        assertThrows(RuntimeException.class, () -> service.exchange(onboarding(email), "127.0.0.1", "test"));
        assertTrue(accounts.findByNormalizedEmail(email).isEmpty());
        assertTrue(identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, subject).isEmpty());
    }

    private AccountJpaEntity account(String email) { AccountJpaEntity a = new AccountJpaEntity(); a.setNormalizedEmail(email); a.setAccountType(AccountType.PATIENT); a.setStatus(AccountStatus.ACTIVE); a.setCreatedAt(java.time.Instant.now()); a.setUpdatedAt(java.time.Instant.now()); return accounts.saveAndFlush(a); }
    private String fixtureEmail(String label) { return "t007-" + label + "-" + runId + "@example.test"; }
    private GoogleOidcService.VerifiedGoogleIdentity identity(String sub, String email) { return new GoogleOidcService.VerifiedGoogleIdentity(sub, email, true); }
    private GoogleExchangeRequest onboarding(String email) { return new GoogleExchangeRequest("c", "v", "r", "New Patient", "0900000000", LocalDate.of(1990, 1, 1), Gender.OTHER, true, false, "v1", "v1"); }
}
