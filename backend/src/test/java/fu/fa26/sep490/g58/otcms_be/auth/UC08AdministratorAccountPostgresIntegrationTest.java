package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.SessionException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.OwnerAdministratorService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@LocalPostgresTest
@Transactional
class UC08AdministratorAccountPostgresIntegrationTest {
    @DynamicPropertySource
    static void testSigningKey(DynamicPropertyRegistry properties) {
        String ephemeralKey = new fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator().generate();
        properties.add("app.auth.session-security.jwt-secret", () -> ephemeralKey);
    }

    @Autowired private AccountRepository accounts;
    @Autowired private OwnerAdministratorService management;
    @Autowired private AuthSessionService sessions;
    @Autowired private PasswordEncoder encoder;
    @Autowired private fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService access;
    @Autowired private fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService tokens;

    @Test
    void administratorRefreshInvalidatesOldAccessSessionAndAllowsNewAccessSession() {
        var account = legacyAdministrator();
        var old = sessions.create(account.getId(), "127.0.0.1", "uc08-rotation-test");
        var next = sessions.rotate(old.refreshToken());
        var oldClaims = tokens.validate(old.body().accessToken());
        var nextClaims = tokens.validate(next.body().accessToken());
        assertNotEquals(oldClaims.sessionId(), nextClaims.sessionId());
        assertEquals(fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService.AccessState.INVALID_SESSION,
                access.evaluate(new AuthenticatedAccount(account.getId(), AccountRole.ADMIN, oldClaims.sessionId())));
        assertEquals(fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService.AccessState.ALLOWED,
                access.evaluate(new AuthenticatedAccount(account.getId(), AccountRole.ADMIN, nextClaims.sessionId())));
    }

    @Test
    void legacyAdministratorCanReceiveIssuedPasswordWithoutUsernameAndOldSessionIsRevoked() {
        var account = legacyAdministrator();
        var old = sessions.create(account.getId(), "127.0.0.1", "uc08-test");
        var response = management.reissuePassword(owner(), account.getId());
        var saved = accounts.findById(account.getId()).orElseThrow();
        assertNull(saved.getUsername());
        assertTrue(saved.isMustChangePassword());
        assertTrue(encoder.matches(response.getTemporaryPassword(), saved.getPasswordHash()));
        assertFalse(encoder.matches("old-password-123", saved.getPasswordHash()));
        assertThrows(SessionException.class, () -> sessions.rotate(old.refreshToken()));
    }

    @Test
    void statusChangesPreserveAccountIdentityAndRole() {
        var account = legacyAdministrator();
        management.deactivate(owner(), account.getId());
        assertEquals(AccountStatus.DISABLED, accounts.findById(account.getId()).orElseThrow().getStatus());
        management.activate(owner(), account.getId());
        var saved = accounts.findById(account.getId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
        assertEquals(AccountType.ADMIN, saved.getAccountType());
    }

    private AccountJpaEntity legacyAdministrator() {
        var account = new AccountJpaEntity();
        account.setAccountType(AccountType.ADMIN);
        account.setStatus(AccountStatus.ACTIVE);
        account.setNormalizedEmail("uc08-" + UUID.randomUUID() + "@example.test");
        account.setPasswordHash(encoder.encode("old-password-123"));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return accounts.saveAndFlush(account);
    }

    private AuthenticatedAccount owner() {
        return new AuthenticatedAccount(UUID.randomUUID(), AccountRole.OWNER, UUID.randomUUID());
    }
}
