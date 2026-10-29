package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.AdministratorAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PasswordResetChallengeRepository;
import fu.fa26.sep490.g58.otcms_be.auth.mapper.AdministratorAccountMapper;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.OwnerAdministratorService;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.AdministratorPasswordIssuer;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.TemporaryPasswordGenerator;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class UC07CreateAdministratorAccountTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthConcurrencyRepository concurrency = mock(AuthConcurrencyRepository.class);
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final AdministratorPasswordIssuer passwords =
            spy(new AdministratorPasswordIssuer(new TemporaryPasswordGenerator(), encoder));
    private final OwnerAdministratorService service = new OwnerAdministratorService(
            accounts, concurrency, mock(AuthSessionService.class),
            mock(PasswordResetChallengeRepository.class),
            passwords,
            new AdministratorAccountMapper());

    @Test
    void ownerCreatesOnlyAdminWithHashedPasswordAndNoStaffProfile(CapturedOutput output) {
        when(accounts.saveAndFlush(any())).thenAnswer(invocation -> {
            AccountJpaEntity account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });
        var response = service.create(actor(AccountRole.OWNER), "Administrator.Name");
        ArgumentCaptor<AccountJpaEntity> saved = ArgumentCaptor.forClass(AccountJpaEntity.class);
        verify(accounts).saveAndFlush(saved.capture());
        AccountJpaEntity account = saved.getValue();
        assertEquals(AccountType.ADMIN, account.getAccountType());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals("Administrator.Name", account.getUsername());
        assertNull(account.getNormalizedEmail());
        assertTrue(account.isMustChangePassword());
        assertTrue(response.isMustChangePassword());
        assertEquals("ADMIN", response.getAccountType());
        assertEquals(account.getId(), response.getAccountId());
        assertEquals(account.getUsername(), response.getUsername());
        verify(passwords).issue(account);
        verify(accounts).existsByUsername("Administrator.Name");
        assertFalse(response.getTemporaryPassword().equals(account.getPasswordHash()));
        assertTrue(encoder.matches(response.getTemporaryPassword(), account.getPasswordHash()));
        assertFalse(response.toString().contains(response.getTemporaryPassword()));
        assertFalse(output.getAll().contains(response.getTemporaryPassword()));
        verify(concurrency).lock("administrator-username:Administrator.Name");
        // The service depends only on Auth persistence; it has no staff-profile dependency.
        verify(accounts, never()).delete(any());
    }

    @Test
    void duplicateUsernameIsRejectedBeforeCredentialCreation() {
        when(accounts.existsByUsername("duplicate")).thenReturn(true);
        var error = assertThrows(AdministratorAccountException.class,
                () -> service.create(actor(AccountRole.OWNER), "duplicate"));
        assertEquals("USERNAME_ALREADY_EXISTS", error.getMessage());
        verify(accounts, never()).saveAndFlush(any());
        verifyNoInteractions(passwords);
    }

    @Test
    void nonOwnerAndAnonymousAreRejectedEvenWithoutController() {
        assertThrows(AccessDeniedException.class, () -> service.create(null, "admin"));
        for (AccountRole role : AccountRole.values()) {
            if (role != AccountRole.OWNER) {
                assertThrows(AccessDeniedException.class, () -> service.create(actor(role), "admin"));
            }
        }
        verifyNoInteractions(accounts, concurrency, passwords);
    }

    @Test
    void blankUsernameIsRejectedWithoutInventingAFormat() {
        for (String username : new String[]{null, "", " \t"}) {
            var error = assertThrows(AdministratorAccountException.class,
                    () -> service.create(actor(AccountRole.OWNER), username));
            assertEquals("VALIDATION_FAILED", error.getMessage());
        }
        verifyNoInteractions(accounts, concurrency);
    }

    @Test
    void invalidUsernameIsRejectedBeforeAnyPersistence() {
        for (String username : new String[]{"a".repeat(65), "admin\u0000", "admin\n", "admin\u007f", "admin\uD800"}) {
            var error = assertThrows(AdministratorAccountException.class,
                    () -> service.create(actor(AccountRole.OWNER), username));
            assertEquals("VALIDATION_FAILED", error.getMessage());
        }
        verifyNoInteractions(accounts, concurrency);
    }

    @Test
    void maximumLengthUnicodeUsernameIsPreserved() {
        String username = "\u1ea3".repeat(64);
        assertEquals(username, service.create(actor(AccountRole.OWNER), username).getUsername());
        verify(accounts).saveAndFlush(any());
    }

    @Test
    void singleCharacterUsernameIsAccepted() {
        // Arrange: the current policy permits any nonblank, printable username of length 1..64.
        String username = "a";

        // Act
        var response = service.create(actor(AccountRole.OWNER), username);

        // Assert
        assertEquals(username, response.getUsername());
        ArgumentCaptor<AccountJpaEntity> saved = ArgumentCaptor.forClass(AccountJpaEntity.class);
        verify(accounts).saveAndFlush(saved.capture());
        assertEquals(username, saved.getValue().getUsername());
        verify(passwords).issue(saved.getValue());
    }

    @Test
    void usernameConstraintViolationIsRejectedWithoutLeakingCredentials() {
        // Arrange: another request claims the username after the initial duplicate check.
        when(accounts.existsByUsername("admin")).thenReturn(false);
        var violation = new org.hibernate.exception.ConstraintViolationException(
                "synthetic-credential-hash", new SQLException("synthetic-temporary-password"),
                "uk_accounts_username");
        when(accounts.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("synthetic-credential-hash", violation));

        // Act
        var error = assertThrows(AdministratorAccountException.class,
                () -> service.create(actor(AccountRole.OWNER), "admin"));

        // Assert: only the public error code survives, without the SQL exception chain.
        assertEquals("USERNAME_ALREADY_EXISTS", error.getMessage());
        assertNull(error.getCause());
        assertEquals(0, error.getSuppressed().length);
        assertFalse(error.toString().contains("synthetic-credential-hash"));
        assertFalse(error.toString().contains("synthetic-temporary-password"));
        verify(accounts).existsByUsername("admin");
        verify(accounts).saveAndFlush(any());
        verify(passwords).issue(any());
    }

    @Test
    void persistenceFailureIsSafeAndDoesNotReturnCredentials() {
        when(accounts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("internal detail"));
        var error = assertThrows(AdministratorAccountException.class,
                () -> service.create(actor(AccountRole.OWNER), "admin"));
        assertEquals("ACCOUNT_CREATION_FAILED", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void generatorMeetsExistingPolicyAndDoesNotRepeatAConstant() {
        TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();
        String first = generator.generate();
        String second = generator.generate();
        assertTrue(first.length() >= 12 && first.length() <= 64);
        assertTrue(first.getBytes(StandardCharsets.UTF_8).length <= 72);
        assertFalse(first.equals(second));
    }

    private AuthenticatedAccount actor(AccountRole role) {
        return new AuthenticatedAccount(UUID.randomUUID(), role, UUID.randomUUID());
    }
}
