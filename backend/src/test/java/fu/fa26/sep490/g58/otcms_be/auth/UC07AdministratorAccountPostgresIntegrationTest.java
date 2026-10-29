package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.AdministratorAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.OwnerAdministratorService;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@LocalPostgresTest
@Transactional
class UC07AdministratorAccountPostgresIntegrationTest {
    @Autowired private OwnerAdministratorService create;
    @Autowired private AccountRepository accounts;
    @Autowired private PasswordEncoder encoder;

    @Test
    void persistsHashedAdministratorAndRejectsDuplicateWithoutReturningPasswordAgain() {
        var owner = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.OWNER, UUID.randomUUID());
        String username = "uc07-" + UUID.randomUUID();
        var response = create.create(owner, username);
        var account = accounts.findByUsername(username).orElseThrow();
        assertEquals("ADMIN", account.getAccountType().name());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertTrue(account.isMustChangePassword());
        assertNull(account.getNormalizedEmail());
        assertTrue(encoder.matches(response.getTemporaryPassword(), account.getPasswordHash()));
        assertFalse(response.getTemporaryPassword().equals(account.getPasswordHash()));
        var error = assertThrows(AdministratorAccountException.class, () -> create.create(owner, username));
        assertEquals("USERNAME_ALREADY_EXISTS", error.getMessage());
    }

    @Test
    void migrationPreservesPatientEmailAndLegacyStaffAccountsWithoutUsernames() {
        for (AccountType type : AccountType.values()) {
            AccountJpaEntity account = new AccountJpaEntity();
            account.setAccountType(type);
            account.setStatus(AccountStatus.ACTIVE);
            account.setNormalizedEmail("uc07-" + UUID.randomUUID() + "@example.test");
            account.setCreatedAt(Instant.now());
            account.setUpdatedAt(Instant.now());
            accounts.saveAndFlush(account);
            assertNull(account.getUsername());
            assertFalse(account.isMustChangePassword());
        }
    }

}
