package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.CredentialLoginException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.SessionException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AuthSessionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthSessionRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthenticationAuditRepository;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.AuthSessionServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.CredentialLoginServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UC07AdministratorLoginTest {
    @Test
    void usernameLoginReusesCredentialRulesAndSessionExposesOnlyChangeFlag() {
        AccountRepository accounts = mock(AccountRepository.class);
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        var generator = new TemporaryPasswordGenerator();
        String issued = generator.generate();
        AccountJpaEntity account = new AccountJpaEntity();
        account.setId(UUID.randomUUID());
        account.setAccountType(AccountType.ADMIN);
        account.setStatus(AccountStatus.ACTIVE);
        account.setUsername("Admin.Name");
        account.setPasswordHash(encoder.encode(issued));
        account.setMustChangePassword(true);
        when(accounts.findByUsername("Admin.Name")).thenReturn(Optional.of(account));
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        var concurrency = mock(AuthConcurrencyRepository.class);
        var properties = new AuthProperties(null, null, null, null, null, null, "v1", "v1", generator.generate());
        var login = new CredentialLoginServiceImpl(accounts, mock(AuthenticationAuditRepository.class),
                encoder, properties, concurrency);
        assertEquals("ADMIN", login.loginAdministrator("Admin.Name", issued, "127.0.0.1").accountType());
        verify(accounts, never()).findByNormalizedEmail(anyString());
        assertThrows(CredentialLoginException.class,
                () -> login.loginAdministrator("missing", issued, "127.0.0.1"));
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        when(sessions.saveAndFlush(any())).thenAnswer(invocation -> {
            AuthSessionJpaEntity session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });
        var security = new SessionSecurityProperties(generator.generate(), 900, 604800,
                "Strict", true, List.of(), List.of());
        var sessionService = new AuthSessionServiceImpl(sessions, accounts,
                new JwtTokenService(security), security, concurrency);
        var result = sessionService.create(account.getId(), "127.0.0.1", "test");
        assertTrue(result.body().isMustChangePassword());
        assertFalse(result.body().getAccessToken().contains(issued));
        AuthSessionJpaEntity session = new AuthSessionJpaEntity();
        session.setAccount(account);
        session.setExpiresAt(Instant.now().plusSeconds(900));
        when(sessions.findByRefreshTokenHashForUpdate(anyString())).thenReturn(Optional.of(session));
        when(sessions.findAccountIdByRefreshTokenHash(anyString())).thenReturn(Optional.of(account.getId()));
        var error = assertThrows(SessionException.class, () -> sessionService.rotate(result.refreshToken()));
        assertEquals("PASSWORD_CHANGE_REQUIRED", error.getMessage());
    }
}
