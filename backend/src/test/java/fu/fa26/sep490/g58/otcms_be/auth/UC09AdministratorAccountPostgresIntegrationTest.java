package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.AdministratorAccountException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.OwnerAdministratorService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@LocalPostgresTest
@Transactional
class UC09AdministratorAccountPostgresIntegrationTest {
    @Autowired private AccountRepository accounts;
    @Autowired private OwnerAdministratorService queries;

    @Test
    void databaseSearchFiltersRoleStatusAndPagesAndHidesEveryOtherRole() {
        String prefix = "uc09-" + UUID.randomUUID();
        var owner = owner();
        for (AccountType type : AccountType.values()) {
            var account = save(prefix + "-" + type.name(), type, AccountStatus.ACTIVE);
            if (type != AccountType.ADMIN) {
                assertThrows(AdministratorAccountException.class, () -> queries.detail(owner, account.getId()));
            }
        }
        save(prefix + "-disabled", AccountType.ADMIN, AccountStatus.DISABLED);
        var all = queries.list(owner, prefix.toUpperCase(java.util.Locale.ROOT), null, 0, 1);
        assertEquals(2, all.getTotalElements());
        assertEquals(2, all.getTotalPages());
        assertTrue(all.stream().allMatch(a -> a.accountType().equals("ADMIN")));
        var next = queries.list(owner, prefix, null, 1, 1);
        assertNotEquals(all.getContent().getFirst().accountId(), next.getContent().getFirst().accountId());
        var disabled = queries.list(owner, prefix, AccountStatus.DISABLED, 0, 20);
        assertEquals(1, disabled.getTotalElements());
        assertEquals("DISABLED", disabled.getContent().getFirst().status());
        assertEquals("ADMIN", queries.detail(owner, all.getContent().getFirst().accountId()).accountType());
    }

    @Test
    void searchEscapesLiteralPercentAndUnderscore() {
        String prefix = "uc09-" + UUID.randomUUID();
        save(prefix + "-%_", AccountType.ADMIN, AccountStatus.ACTIVE);
        save(prefix + "-other", AccountType.ADMIN, AccountStatus.ACTIVE);
        assertEquals(1, queries.list(owner(), prefix + "-%_", null, 0, 20).getTotalElements());
    }

    private AccountJpaEntity save(String username, AccountType type, AccountStatus status) {
        var account = new AccountJpaEntity();
        account.setUsername(username);
        account.setAccountType(type);
        account.setStatus(status);
        account.setNormalizedEmail(UUID.randomUUID() + "@example.test");
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        return accounts.saveAndFlush(account);
    }

    private AuthenticatedAccount owner() {
        return new AuthenticatedAccount(UUID.randomUUID(), AccountRole.OWNER, UUID.randomUUID());
    }
}
