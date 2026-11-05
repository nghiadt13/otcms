package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.CredentialLoginException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.AccountAccessSnapshot;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.security.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService.AccessState;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import jakarta.servlet.FilterChain;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Kan27StaffLoginAndAccessTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthenticationAuditRepository audits = mock(AuthenticationAuditRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthConcurrencyRepository locks = mock(AuthConcurrencyRepository.class);
    private final CredentialLoginServiceImpl login = new CredentialLoginServiceImpl(accounts, audits, encoder,
            new AuthProperties(null, null, null, null, null, null, "v1", "v1", "test-only-hmac"), locks);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void exactAtUsernameWinsOverLegacyEmailAndWrongPasswordDoesNotFallBack() {
        var account = staff();
        when(accounts.findByUsername("doctor@clinic")).thenReturn(Optional.of(account));
        when(encoder.matches("right", "hash")).thenReturn(true);
        assertEquals(account.getId(), login.loginStaff("doctor@clinic", "right", "127.0.0.1").accountId());
        assertThrows(CredentialLoginException.class, () -> login.loginStaff("doctor@clinic", "wrong", "127.0.0.1"));
        assertEquals(1, account.getFailedCredentialCount());
        verify(accounts, never()).findByNormalizedEmail(any());
        verify(audits, times(4)).save(any()); // one IP attempt and one result per request
        var order = inOrder(locks, accounts);
        order.verify(locks).lock(startsWith("login-ip:"));
        order.verify(accounts).findByUsername("doctor@clinic");
    }

    @Test void usernameMissFallsBackToLegacyEmailWithSingleAttempt() {
        var account = staff();
        when(accounts.findByNormalizedEmail("legacy@example.test")).thenReturn(Optional.of(account));
        when(encoder.matches("right", "hash")).thenReturn(true);
        assertEquals(account.getId(), login.loginStaff(" Legacy@Example.Test ", "right", "127.0.0.1").accountId());
        verify(accounts).findByUsername(" Legacy@Example.Test ");
        verify(accounts).findByNormalizedEmail("legacy@example.test");
        verify(audits, times(2)).save(any());
        assertEquals(0, account.getFailedCredentialCount());
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"ADMIN", "RECEPTIONIST", "DOCTOR", "LAB_TECHNICIAN", "IMAGING_TECHNICIAN"})
    void fullEligibilityPrecedesForcedChangeAndRevokedJwtNeverRevives(AccountType type) throws Exception {
        var sessions = mock(AuthSessionRepository.class);
        var tokens = mock(JwtTokenService.class);
        UUID accountId = UUID.randomUUID(), sessionId = UUID.randomUUID();
        var access = new AccountAccessServiceImpl(accounts, sessions);
        var principal = new AuthenticatedAccount(accountId, AccountRole.valueOf(type.name()), sessionId);
        when(accounts.findAccessSnapshotById(accountId)).thenReturn(Optional.of(new AccountAccessSnapshot(type, AccountStatus.ACTIVE, true)));
        when(tokens.validate("old")).thenReturn(new JwtTokenService.Claims(accountId, type, sessionId, 0, Long.MAX_VALUE));
        var filter = new JwtAuthenticationFilter(tokens, access);
        assertEquals(AccessState.INVALID_SESSION, access.evaluate(principal));
        assertEquals(401, perform(filter, "/api/auth/change-password", "POST"));
        when(sessions.existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(eq(sessionId), eq(accountId), any())).thenReturn(true);
        assertEquals(AccessState.PASSWORD_CHANGE_REQUIRED, access.evaluate(principal));
        assertEquals(403, perform(filter, "/api/v1/clinic-services", "GET"));
        assertEquals(200, perform(filter, "/api/auth/change-password", "POST"));
        when(accounts.findAccessSnapshotById(accountId)).thenReturn(Optional.of(new AccountAccessSnapshot(type, AccountStatus.DISABLED, false)));
        assertEquals(401, perform(filter, "/api/v1/clinic-services", "GET"));
        when(sessions.existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(eq(sessionId), eq(accountId), any())).thenReturn(false);
        when(accounts.findAccessSnapshotById(accountId)).thenReturn(Optional.of(new AccountAccessSnapshot(type, AccountStatus.ACTIVE, false)));
        assertEquals(401, perform(filter, "/api/v1/clinic-services", "GET"));
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"PATIENT", "OWNER"})
    void sharedFixDoesNotBroadenPatientOrOwnerEligibility(AccountType type) {
        var sessions = mock(AuthSessionRepository.class);
        UUID id = UUID.randomUUID();
        when(accounts.findAccessSnapshotById(id)).thenReturn(Optional.of(new AccountAccessSnapshot(type, AccountStatus.DISABLED, false)));
        assertEquals(AccessState.ALLOWED, new AccountAccessServiceImpl(accounts, sessions)
                .evaluate(new AuthenticatedAccount(id, AccountRole.valueOf(type.name()), UUID.randomUUID())));
        verifyNoInteractions(sessions);
    }

    private int perform(JwtAuthenticationFilter filter, String path, String method) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer old");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response.getStatus();
    }
    private AccountJpaEntity staff() {
        var account = new AccountJpaEntity();
        account.setId(UUID.randomUUID());
        account.setAccountType(AccountType.DOCTOR);
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash("hash");
        return account;
    }
}
