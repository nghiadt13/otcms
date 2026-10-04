package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.dto.GoogleExchangeRequest;
import fu.fa26.sep490.g58.otcms_be.auth.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.port.GoogleOidcPort;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.auth.session-security.jwt-secret=t007-postgres-secret-32-bytes-minimum",
        "app.auth.terms-version=v1", "app.auth.privacy-version=v1"
})
@Testcontainers(disabledWithoutDocker = true)
class T007GoogleOnboardingPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername); r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired GoogleAuthenticationService service;
    @Autowired AccountRepository accounts;
    @Autowired ExternalIdentityRepository identities;
    @MockitoBean PatientProfileRepository profiles;
    @MockitoBean ConsentEvidenceRepository consents;
    @Autowired AuthSessionRepository sessions;
    @MockitoBean GoogleOidcPort provider;

    @AfterEach void cleanup() { sessions.deleteAll(); identities.deleteAll(); consents.deleteAll(); profiles.deleteAll(); accounts.deleteAll(); }

    @Test void missingOnboardingDataDoesNotCreateAccountOrIdentity() {
        when(provider.exchange(any(), any(), any())).thenReturn(identity("new-sub", "new@example.test"));
        assertThrows(GoogleAuthenticationException.class, () -> service.exchange(new GoogleExchangeRequest("c", "v", "r"), "127.0.0.1", "test"));
        assertTrue(accounts.findByNormalizedEmail("new@example.test").isEmpty());
        assertTrue(identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "new-sub").isEmpty());
    }

    @Test void providerSubjectLinkedToDifferentEmailRollsBackWithoutNewLinkage() {
        Account owner = account("owner@example.test");
        ExternalIdentity existing = new ExternalIdentity(); existing.setAccount(owner); existing.setProvider(IdentityProvider.GOOGLE); existing.setProviderSubject("shared-sub"); existing.setNormalizedEmailAtLink(owner.getNormalizedEmail()); existing.setLinkedAt(java.time.Instant.now()); identities.saveAndFlush(existing);
        when(provider.exchange(any(), any(), any())).thenReturn(identity("shared-sub", "attacker@example.test"));
        assertThrows(GoogleAuthenticationException.class, () -> service.exchange(onboarding("attacker@example.test"), "127.0.0.1", "test"));
        assertTrue(accounts.findByNormalizedEmail("attacker@example.test").isEmpty());
        assertEquals(1, identities.count()); assertEquals(owner.getId(), identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "shared-sub").orElseThrow().getAccount().getId());
    }

    @Test void profilePersistenceFailureRollsBackAccountAndLinkage() {
        when(provider.exchange(any(), any(), any())).thenReturn(identity("rollback-sub", "rollback@example.test"));
        doThrow(new IllegalStateException("simulated profile persistence failure")).when(profiles).save(any(PatientProfile.class));
        var request = onboarding("rollback@example.test");
        assertThrows(RuntimeException.class, () -> service.exchange(request, "127.0.0.1", "test"));
        assertTrue(accounts.findByNormalizedEmail("rollback@example.test").isEmpty());
        assertTrue(identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "rollback-sub").isEmpty());
    }

    @Test void consentPersistenceFailureRollsBackAccountAndLinkage() {
        when(provider.exchange(any(), any(), any())).thenReturn(identity("consent-rollback-sub", "consent-rollback@example.test"));
        doThrow(new IllegalStateException("simulated consent persistence failure")).when(consents).save(any(ConsentEvidence.class));
        assertThrows(RuntimeException.class, () -> service.exchange(onboarding("consent-rollback@example.test"), "127.0.0.1", "test"));
        assertTrue(accounts.findByNormalizedEmail("consent-rollback@example.test").isEmpty());
        assertTrue(identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, "consent-rollback-sub").isEmpty());
    }

    private Account account(String email) { Account a = new Account(); a.setNormalizedEmail(email); a.setAccountType(AccountType.PATIENT); a.setStatus(AccountStatus.ACTIVE); a.setCreatedAt(java.time.Instant.now()); a.setUpdatedAt(java.time.Instant.now()); return accounts.saveAndFlush(a); }
    private GoogleOidcPort.VerifiedGoogleIdentity identity(String sub, String email) { return new GoogleOidcPort.VerifiedGoogleIdentity(sub, email, true); }
    private GoogleExchangeRequest onboarding(String email) { return new GoogleExchangeRequest("c", "v", "r", "New Patient", "0900000000", LocalDate.of(1990, 1, 1), Gender.OTHER, true, false, "v1", "v1"); }
}
