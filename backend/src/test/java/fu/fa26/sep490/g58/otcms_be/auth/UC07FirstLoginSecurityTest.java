package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ChangePasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AuthSessionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthSessionRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PasswordResetChallengeRepository;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtAuthenticationFilter;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.AccountAccessServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.PasswordRecoveryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UC07FirstLoginSecurityTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final AccountJpaEntity account = new AccountJpaEntity();
    private final AuthSessionJpaEntity session = new AuthSessionJpaEntity();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens,
            new AccountAccessServiceImpl(accounts, sessions));

    @BeforeEach
    void setup() {
        account.setId(UUID.randomUUID());
        account.setUsername("first-login");
        account.setAccountType(AccountType.ADMIN);
        account.setStatus(AccountStatus.ACTIVE);
        account.setMustChangePassword(true);
        session.setId(UUID.randomUUID());
        session.setAccount(account);
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        when(accounts.findAccessSnapshotById(account.getId())).thenAnswer(invocation -> Optional.of(
                new fu.fa26.sep490.g58.otcms_be.auth.model.dto.AccountAccessSnapshot(
                        account.getAccountType(), account.getStatus(), account.isMustChangePassword())));
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(sessions.existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(
                eq(session.getId()), eq(account.getId()), any())).thenAnswer(invocation -> session.getRevokedAt() == null);
        when(tokens.validate("issued-access")).thenReturn(new JwtTokenService.Claims(
                account.getId(), AccountType.ADMIN, session.getId(), 0, Long.MAX_VALUE));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void issuedPasswordSessionCannotAccessBusinessOrPublicFunctions() throws Exception {
        for (String path : new String[]{"/api/v1/admin/clinic-services", "/api/v1/clinic-services",
                "/api/v1/owner/administrator-accounts", "/api/auth/staff/login"}) {
            FilterChain chain = mock(FilterChain.class);
            var response = perform("GET", path, chain);
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
            verifyNoInteractions(chain);
        }
    }

    @Test
    void onlyPasswordChangeLogoutAndCsrfAreAllowedBeforeChange() throws Exception {
        for (String path : new String[]{"/api/auth/change-password", "/api/auth/logout", "/api/auth/csrf"}) {
            FilterChain chain = mock(FilterChain.class);
            var response = perform(path.endsWith("csrf") ? "GET" : "POST", path, chain);
            assertEquals(200, response.getStatus());
            verify(chain).doFilter(any(), any());
        }
        assertEquals(403, perform("GET", "/api/auth/change-password", mock(FilterChain.class)).getStatus());
    }

    @Test
    void successfulChangeClearsFlagAndOldAccessTokenRemainsRevoked() throws Exception {
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        var generator = new TemporaryPasswordGenerator();
        String issued = generator.generate();
        String replacement = generator.generate();
        account.setPasswordHash(encoder.encode(issued));
        AuthSessionService authSessions = mock(AuthSessionService.class);
        doAnswer(invocation -> {
            session.setRevokedAt(Instant.now());
            return null;
        }).when(authSessions).revokeAll(account.getId());
        var recovery = new PasswordRecoveryServiceImpl(accounts, mock(PasswordResetChallengeRepository.class),
                mock(EmailDeliveryService.class), encoder, authSessions, mock(AuthProperties.class));
        recovery.change(account.getId(), new ChangePasswordRequest(issued, replacement));
        assertFalse(account.isMustChangePassword());
        assertTrue(encoder.matches(replacement, account.getPasswordHash()));
        assertFalse(encoder.matches(issued, account.getPasswordHash()));
        assertEquals(401, perform("GET", "/api/v1/admin/clinic-services", mock(FilterChain.class)).getStatus());
        session.setRevokedAt(null); // A subsequent login creates a fresh active session.
        FilterChain chain = mock(FilterChain.class);
        perform("GET", "/api/v1/admin/clinic-services", chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void failedChangeKeepsFlagAndCredential() {
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        account.setPasswordHash(encoder.encode(new TemporaryPasswordGenerator().generate()));
        String oldHash = account.getPasswordHash();
        var authSessions = mock(AuthSessionService.class);
        var recovery = new PasswordRecoveryServiceImpl(accounts, mock(PasswordResetChallengeRepository.class),
                mock(EmailDeliveryService.class), encoder, authSessions, mock(AuthProperties.class));
        assertThrows(RuntimeException.class, () -> recovery.change(account.getId(),
                new ChangePasswordRequest("incorrect", new TemporaryPasswordGenerator().generate())));
        assertTrue(account.isMustChangePassword());
        assertEquals(oldHash, account.getPasswordHash());
        verifyNoInteractions(authSessions);
    }

    private MockHttpServletResponse perform(String method, String path, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer issued-access");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
