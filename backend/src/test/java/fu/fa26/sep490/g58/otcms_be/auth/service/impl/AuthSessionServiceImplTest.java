package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.SessionException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AuthSessionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthSessionRepository;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final String REFRESH_TOKEN = "refresh-token-dummy";

    @Mock
    private AuthSessionRepository sessions;
    @Mock
    private AccountRepository accounts;
    @Mock
    private JwtTokenService jwt;
    @Mock
    private AuthConcurrencyRepository concurrency;

    private AuthSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        SessionSecurityProperties properties = new SessionSecurityProperties(
                "jwt-secret-dummy-0123456789abcdef0123", 900L, 2_592_000L, "Lax", false, List.of(), List.of());
        service = new AuthSessionServiceImpl(sessions, accounts, jwt, properties, concurrency,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AccountJpaEntity account(AccountStatus status) {
        AccountJpaEntity account = new AccountJpaEntity();
        account.setId(ACCOUNT_ID);
        account.setAccountType(AccountType.PATIENT);
        account.setStatus(status);
        return account;
    }

    private static AuthSessionJpaEntity session(AccountJpaEntity account, Instant expiresAt) {
        AuthSessionJpaEntity session = new AuthSessionJpaEntity();
        session.setAccount(account);
        session.setTokenFamilyId(UUID.randomUUID());
        session.setRefreshTokenHash("refresh-hash-dummy");
        session.setCreatedAt(NOW.minusSeconds(60));
        session.setExpiresAt(expiresAt);
        return session;
    }

    private void stubRotateLookup(AuthSessionJpaEntity previous) {
        when(sessions.findAccountIdByRefreshTokenHash(anyString())).thenReturn(Optional.of(ACCOUNT_ID));
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(previous.getAccount()));
        when(sessions.findByRefreshTokenHashForUpdate(anyString())).thenReturn(Optional.of(previous));
    }

    @Test
    void createActiveAccountIssuesTokensAndSavesSession() {
        AccountJpaEntity active = account(AccountStatus.ACTIVE);
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(active));
        when(sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(ACCOUNT_ID)).thenReturn(List.of());
        when(jwt.issue(eq(ACCOUNT_ID), eq(AccountType.PATIENT), any())).thenReturn("access-jwt");

        var issued = service.create(ACCOUNT_ID, "203.0.113.10", "agent-dummy");

        assertEquals("access-jwt", issued.body().getAccessToken());
        assertNotNull(issued.refreshToken());
        verify(sessions).saveAndFlush(any(AuthSessionJpaEntity.class));
    }

    @Test
    void rotateActiveRefreshTokenIssuesNewTokensAndRevokesPrevious() {
        AccountJpaEntity active = account(AccountStatus.ACTIVE);
        AuthSessionJpaEntity previous = session(active, NOW.plusSeconds(3600));
        stubRotateLookup(previous);
        when(jwt.issue(eq(ACCOUNT_ID), eq(AccountType.PATIENT), any())).thenReturn("rotated-access-jwt");

        var rotated = service.rotate(REFRESH_TOKEN);

        assertEquals("rotated-access-jwt", rotated.body().getAccessToken());
        assertEquals(NOW, previous.getRevokedAt());
        assertEquals("ROTATED", previous.getRevokeReason());
        verify(sessions).saveAndFlush(any(AuthSessionJpaEntity.class));
    }

    @Test
    void accountTypeForRefreshWhenRefreshTokenKnownReturnsRepositoryAccountType() {
        when(sessions.findAccountTypeByRefreshTokenHash(anyString())).thenReturn(Optional.of(AccountType.DOCTOR));

        assertEquals(Optional.of(AccountType.DOCTOR), service.accountTypeForRefresh(REFRESH_TOKEN));
    }

    @Test
    void createWhenAccountNotActiveThrowsAuthenticationFailed() {
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account(AccountStatus.DISABLED)));

        SessionException ex = assertThrows(SessionException.class,
                () -> service.create(ACCOUNT_ID, "203.0.113.10", "agent-dummy"));

        assertEquals("AUTHENTICATION_FAILED", ex.getMessage());
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void rotateWhenRefreshTokenBlankThrowsAuthenticationFailed() {
        SessionException ex = assertThrows(SessionException.class, () -> service.rotate("   "));

        assertEquals("AUTHENTICATION_FAILED", ex.getMessage());
        verifyNoInteractions(sessions, accounts);
    }

    @Test
    void rotateWhenAccountNotActiveRevokesSessionAndThrows() {
        AuthSessionJpaEntity previous = session(account(AccountStatus.DISABLED), NOW.plusSeconds(3600));
        stubRotateLookup(previous);

        assertThrows(SessionException.class, () -> service.rotate(REFRESH_TOKEN));

        assertEquals(NOW, previous.getRevokedAt());
        assertEquals("INVALID_REFRESH", previous.getRevokeReason());
        verify(sessions).save(previous);
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void rotateWhenRefreshTokenAtExactExpirationBoundaryRevokesAndThrows() {
        AuthSessionJpaEntity previous = session(account(AccountStatus.ACTIVE), NOW);
        stubRotateLookup(previous);

        SessionException ex = assertThrows(SessionException.class, () -> service.rotate(REFRESH_TOKEN));

        assertEquals("AUTHENTICATION_FAILED", ex.getMessage());
        assertEquals(NOW, previous.getRevokedAt());
        assertEquals("INVALID_REFRESH", previous.getRevokeReason());
        verify(sessions).save(previous);
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void createEvictsOldestActiveSessionsWhenAtFiveSessionLimit() {
        AccountJpaEntity active = account(AccountStatus.ACTIVE);
        AuthSessionJpaEntity oldest = session(active, NOW.plusSeconds(3600));
        AuthSessionJpaEntity second = session(active, NOW.plusSeconds(3600));
        AuthSessionJpaEntity third = session(active, NOW.plusSeconds(3600));
        AuthSessionJpaEntity fourth = session(active, NOW.plusSeconds(3600));
        AuthSessionJpaEntity fifth = session(active, NOW.plusSeconds(3600));
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(active));
        when(sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(ACCOUNT_ID))
                .thenReturn(List.of(oldest, second, third, fourth, fifth));
        when(jwt.issue(eq(ACCOUNT_ID), eq(AccountType.PATIENT), any())).thenReturn("access-jwt");

        service.create(ACCOUNT_ID, "203.0.113.10", "agent-dummy", false);

        assertEquals(NOW, oldest.getRevokedAt());
        assertEquals("SESSION_LIMIT", oldest.getRevokeReason());
        assertNull(second.getRevokedAt());
        verify(sessions).save(oldest);
        verify(sessions).saveAndFlush(argThat(s -> !s.isRememberMe()));
    }

    @Test
    void rotateRevokedTokenRevokesEntireTokenFamilyAsTokenReuse() {
        AccountJpaEntity active = account(AccountStatus.ACTIVE);
        AuthSessionJpaEntity previous = session(active, NOW.plusSeconds(3600));
        previous.setRevokedAt(NOW.minusSeconds(10));
        previous.setRevokeReason("ROTATED");
        AuthSessionJpaEntity sibling = session(active, NOW.plusSeconds(3600));
        sibling.setTokenFamilyId(previous.getTokenFamilyId());
        stubRotateLookup(previous);
        when(sessions.findAllByTokenFamilyIdAndRevokedAtIsNull(previous.getTokenFamilyId(), PageRequest.of(0, 1000)))
                .thenReturn(new PageImpl<>(List.of(sibling)));

        SessionException ex = assertThrows(SessionException.class, () -> service.rotate(REFRESH_TOKEN));

        assertEquals("AUTHENTICATION_FAILED", ex.getMessage());
        assertEquals(NOW, sibling.getRevokedAt());
        assertEquals("TOKEN_REUSE", sibling.getRevokeReason());
        verify(sessions).save(sibling);
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void rotateWhenMustChangePasswordThrowsPasswordChangeRequiredWithoutRevoking() {
        AccountJpaEntity active = account(AccountStatus.ACTIVE);
        active.setMustChangePassword(true);
        AuthSessionJpaEntity previous = session(active, NOW.plusSeconds(3600));
        stubRotateLookup(previous);

        SessionException ex = assertThrows(SessionException.class, () -> service.rotate(REFRESH_TOKEN));

        assertEquals("PASSWORD_CHANGE_REQUIRED", ex.getMessage());
        assertNull(previous.getRevokedAt());
        verify(sessions, never()).save(any());
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void logoutRevokesKnownSessionAndIgnoresBlankOrNullTokens() {
        service.logout(null);
        service.logout("  ");
        verifyNoInteractions(sessions);

        AuthSessionJpaEntity active = session(account(AccountStatus.ACTIVE), NOW.plusSeconds(3600));
        when(sessions.findByRefreshTokenHashForUpdate(anyString())).thenReturn(Optional.of(active));

        service.logout(REFRESH_TOKEN);

        assertEquals(NOW, active.getRevokedAt());
        assertEquals("LOGOUT", active.getRevokeReason());
        verify(sessions).save(active);
    }

    @Test
    void revokeAllRevokesAllActiveSessionsAndRejectsMissingAccount() {
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.empty());
        assertEquals("AUTHENTICATION_FAILED", assertThrows(SessionException.class,
                () -> service.revokeAll(ACCOUNT_ID)).getMessage());

        AccountJpaEntity active = account(AccountStatus.ACTIVE);
        AuthSessionJpaEntity s1 = session(active, NOW.plusSeconds(3600));
        AuthSessionJpaEntity s2 = session(active, NOW.plusSeconds(7200));
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(active));
        when(sessions.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(ACCOUNT_ID))
                .thenReturn(List.of(s1, s2));

        service.revokeAll(ACCOUNT_ID);

        verify(concurrency).lock("auth-session:" + ACCOUNT_ID);
        assertEquals("PASSWORD_CHANGED", s1.getRevokeReason());
        assertEquals("PASSWORD_CHANGED", s2.getRevokeReason());
        verify(sessions).save(s1);
        verify(sessions).save(s2);
    }
}
