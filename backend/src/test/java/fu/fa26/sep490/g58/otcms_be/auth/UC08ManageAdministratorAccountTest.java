package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.AdministratorAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.CredentialLoginException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.PasswordRecoveryException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.SessionException;
import fu.fa26.sep490.g58.otcms_be.auth.mapper.AdministratorAccountMapper;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ResetPasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AuthSessionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PasswordResetChallengeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.OwnerAdministratorService;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class UC08ManageAdministratorAccountTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final PasswordResetChallengeRepository resets = mock(PasswordResetChallengeRepository.class);
    private final org.springframework.security.crypto.password.PasswordEncoder encoder =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();
    private final OwnerAdministratorService service = new OwnerAdministratorService(
            accounts, mock(AuthConcurrencyRepository.class), sessions, resets, new AdministratorPasswordIssuer(generator, encoder),
            new AdministratorAccountMapper());

    @Test
    void deactivateAndActivatePreserveRoleCredentialsAndAccount() {
        var account = target(AccountType.ADMIN);
        String hash = account.getPasswordHash();
        var disabled = service.deactivate(actor(AccountRole.OWNER), account.getId());
        assertEquals("DISABLED", disabled.status());
        assertEquals(AccountType.ADMIN, account.getAccountType());
        assertEquals(hash, account.getPasswordHash());
        verify(sessions).revokeAll(account.getId(), "ACCOUNT_DEACTIVATED");
        var active = service.activate(actor(AccountRole.OWNER), account.getId());
        assertEquals("ACTIVE", active.status());
        assertEquals(AccountType.ADMIN, account.getAccountType());
        assertEquals("legacy@example.test", account.getNormalizedEmail());
        verify(accounts, times(2)).saveAndFlush(account);
        verify(accounts, never()).delete(any());
        verify(accounts, never()).deleteById(any());
        verifyNoInteractions(resets);
    }

    @Test
    void allNonOwnersAndAnonymousAreDeniedBeforeLookup() {
        for (AccountRole role : AccountRole.values()) {
            if (role == AccountRole.OWNER) continue;
            assertDenied(actor(role));
        }
        assertDenied(null);
        verifyNoInteractions(accounts, sessions, resets);
    }

    @Test
    void allOtherTargetRolesAndMissingAccountAreHiddenAndUnmodified() {
        UUID id = UUID.randomUUID();
        assertNotFound(id);
        for (AccountType type : AccountType.values()) {
            if (type == AccountType.ADMIN) continue;
            var account = target(type);
            assertNotFound(account.getId());
            assertEquals(type, account.getAccountType());
            assertEquals(AccountStatus.ACTIVE, account.getStatus());
        }
        verify(accounts, never()).saveAndFlush(any());
        verifyNoInteractions(sessions, resets);
    }

    @Test
    void reissueHashesNewPasswordAndReturnsItOnlyOnceWithoutLogging(CapturedOutput output) {
        var account = target(AccountType.ADMIN);
        String oldHash = account.getPasswordHash();
        var response = service.reissuePassword(actor(AccountRole.OWNER), account.getId());
        assertNotEquals(oldHash, account.getPasswordHash());
        assertNotEquals(response.getTemporaryPassword(), account.getPasswordHash());
        assertTrue(encoder.matches(response.getTemporaryPassword(), account.getPasswordHash()));
        assertFalse(encoder.matches("old-password-123", account.getPasswordHash()));
        assertTrue(account.isMustChangePassword());
        assertTrue(response.isMustChangePassword());
        assertNull(account.getUsername()); // Legacy email-based ADMIN remains supported.
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(AccountType.ADMIN, account.getAccountType());
        verify(sessions).revokeAll(account.getId());
        verify(resets).supersedeCurrent(eq(account.getId()), any());
        assertFalse(response.toString().contains(response.getTemporaryPassword()));
        assertFalse(output.getAll().contains(response.getTemporaryPassword()));
        var mapper = JsonMapper.builder().build();
        String body = mapper.writeValueAsString(response);
        assertFalse(body.contains("passwordHash"));
        assertFalse(body.contains(account.getPasswordHash()));
        String safe = mapper.writeValueAsString(new AdministratorAccountMapper().toResponse(account));
        assertFalse(safe.contains(response.getTemporaryPassword()));
        assertFalse(safe.contains("temporaryPassword"));
    }

    @Test
    void oldPasswordFailsAndNewPasswordAuthenticatesWithFirstLoginRestriction() {
        var account = target(AccountType.ADMIN);
        var issued = service.reissuePassword(actor(AccountRole.OWNER), account.getId());
        var login = new CredentialLoginServiceImpl(accounts, mock(AuthenticationAuditRepository.class), encoder,
                properties(), mock(AuthConcurrencyRepository.class));
        when(accounts.findByNormalizedEmail(account.getNormalizedEmail())).thenReturn(Optional.of(account));
        assertThrows(CredentialLoginException.class,
                () -> login.loginStaff(account.getNormalizedEmail(), "old-password-123", "127.0.0.1"));
        assertEquals("ADMIN", login.loginStaff(account.getNormalizedEmail(), issued.getTemporaryPassword(),
                "127.0.0.1").accountType());
        AuthSessionRepository sessionRepository = mock(AuthSessionRepository.class);
        AuthSessionJpaEntity session = session(account);
        snapshot(account);
        when(sessionRepository.existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(
                eq(session.getId()), eq(account.getId()), any())).thenReturn(true);
        var access = new AccountAccessServiceImpl(accounts, sessionRepository);
        assertEquals(AccountAccessService.AccessState.PASSWORD_CHANGE_REQUIRED,
                access.evaluate(new AuthenticatedAccount(account.getId(), AccountRole.ADMIN, session.getId())));
    }

    @Test
    void reissueRevokesAccessAndRefreshTokensForLegacyAdmin() {
        var account = target(AccountType.ADMIN);
        var repository = mock(AuthSessionRepository.class);
        var old = session(account);
        when(repository.findAllByAccountIdAndRevokedAtIsNullOrderByCreatedAtAscIdAsc(account.getId()))
                .thenReturn(List.of(old));
        when(repository.existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(
                eq(old.getId()), eq(account.getId()), any())).thenAnswer(invocation -> old.getRevokedAt() == null);
        when(repository.findAccountIdByRefreshTokenHash(anyString())).thenReturn(Optional.of(account.getId()));
        when(repository.findByRefreshTokenHashForUpdate(anyString())).thenReturn(Optional.of(old));
        when(repository.findAllByTokenFamilyIdAndRevokedAtIsNull(any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        snapshot(account);
        var properties = new SessionSecurityProperties(generator.generate(), 900, 604800,
                "Strict", true, List.of(), List.of());
        var realSessions = new AuthSessionServiceImpl(repository, accounts, new JwtTokenService(properties),
                properties, mock(AuthConcurrencyRepository.class));
        var management = new OwnerAdministratorService(accounts, mock(AuthConcurrencyRepository.class), realSessions, resets,
                new AdministratorPasswordIssuer(generator, encoder), new AdministratorAccountMapper());
        management.reissuePassword(actor(AccountRole.OWNER), account.getId());
        assertNotNull(old.getRevokedAt());
        assertThrows(SessionException.class, () -> realSessions.rotate("old-refresh"));
        assertEquals(AccountAccessService.AccessState.INVALID_SESSION,
                new AccountAccessServiceImpl(accounts, repository).evaluate(
                        new AuthenticatedAccount(account.getId(), AccountRole.ADMIN, old.getId())));
    }

    @Test
    void disabledAccountCannotBeResurrectedByFailedLoginOrOldResetLink() {
        var account = target(AccountType.ADMIN);
        service.deactivate(actor(AccountRole.OWNER), account.getId());
        account.setFailedCredentialCount(4);
        when(accounts.findByNormalizedEmail(account.getNormalizedEmail())).thenReturn(Optional.of(account));
        var login = new CredentialLoginServiceImpl(accounts, mock(AuthenticationAuditRepository.class), encoder,
                properties(), mock(AuthConcurrencyRepository.class));
        assertThrows(CredentialLoginException.class,
                () -> login.loginStaff(account.getNormalizedEmail(), "incorrect", "127.0.0.1"));
        assertEquals(AccountStatus.DISABLED, account.getStatus());
        var challenge = new PasswordResetChallengeJpaEntity();
        challenge.setAccount(account);
        challenge.setExpiresAt(Instant.now().plusSeconds(600));
        when(resets.findAccountIdByTokenHash(anyString())).thenReturn(Optional.of(account.getId()));
        when(resets.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(challenge));
        var recovery = new PasswordRecoveryServiceImpl(accounts, resets, mock(EmailDeliveryService.class),
                encoder, sessions, properties());
        assertThrows(PasswordRecoveryException.class, () -> recovery.reset(
                new ResetPasswordRequest("old-reset-link", "new-password-123"), AccountType.RECEPTIONIST));
        assertEquals(AccountStatus.DISABLED, account.getStatus());
    }

    @Test
    void reissueDoesNotActivateDisabledAccount() {
        var account = target(AccountType.ADMIN);
        account.setStatus(AccountStatus.DISABLED);
        service.reissuePassword(actor(AccountRole.OWNER), account.getId());
        assertEquals(AccountStatus.DISABLED, account.getStatus());
    }

    private void assertDenied(AuthenticatedAccount actor) {
        UUID id = UUID.randomUUID();
        assertThrows(AccessDeniedException.class, () -> service.activate(actor, id));
        assertThrows(AccessDeniedException.class, () -> service.deactivate(actor, id));
        assertThrows(AccessDeniedException.class, () -> service.reissuePassword(actor, id));
    }

    private void assertNotFound(UUID id) {
        assertEquals("ADMINISTRATOR_ACCOUNT_NOT_FOUND", assertThrows(AdministratorAccountException.class,
                () -> service.activate(actor(AccountRole.OWNER), id)).getMessage());
        assertThrows(AdministratorAccountException.class, () -> service.deactivate(actor(AccountRole.OWNER), id));
        assertThrows(AdministratorAccountException.class, () -> service.reissuePassword(actor(AccountRole.OWNER), id));
    }

    private AccountJpaEntity target(AccountType type) {
        var account = new AccountJpaEntity();
        account.setId(UUID.randomUUID());
        account.setAccountType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setNormalizedEmail("legacy@example.test");
        account.setPasswordHash(encoder.encode("old-password-123"));
        account.setCreatedAt(Instant.EPOCH);
        account.setUpdatedAt(Instant.EPOCH);
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        return account;
    }

    private AuthSessionJpaEntity session(AccountJpaEntity account) {
        var session = new AuthSessionJpaEntity();
        session.setId(UUID.randomUUID());
        session.setAccount(account);
        session.setTokenFamilyId(UUID.randomUUID());
        session.setExpiresAt(Instant.now().plusSeconds(600));
        return session;
    }

    private AuthenticatedAccount actor(AccountRole role) {
        return new AuthenticatedAccount(UUID.randomUUID(), role, UUID.randomUUID());
    }

    private AuthProperties properties() {
        return new AuthProperties(null, null, null, null, null, null, "v1", "v1", generator.generate());
    }

    private void snapshot(AccountJpaEntity account) {
        when(accounts.findAccessSnapshotById(account.getId())).thenAnswer(invocation -> Optional.of(
                new fu.fa26.sep490.g58.otcms_be.auth.model.dto.AccountAccessSnapshot(
                        account.getAccountType(), account.getStatus(), account.isMustChangePassword())));
    }
}
