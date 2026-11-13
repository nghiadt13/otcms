package fu.fa26.sep490.g58.otcms_be.auth;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.VerificationCodeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.VerificationPurpose;
import fu.fa26.sep490.g58.otcms_be.auth.repository.VerificationCodeRepository;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** final-db V110: one table for every OTP and reset token; each repository method is scoped by purpose. */
@SpringBootTest
@LocalPostgresTest
class VerificationCodeRepositoryPostgresIntegrationTest {
    private static final String EMAIL_PREFIX = "t110-vc-";

    @Autowired VerificationCodeRepository codes;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() {
        // verification_codes rows go with their account (ON DELETE CASCADE).
        jdbc.update("delete from accounts where normalized_email like ?", EMAIL_PREFIX + "%");
    }

    @Test
    void oneOpenCodePerAccountAndPurposeWhilePurposesStayIndependent() {
        UUID account = account();
        codes.saveAndFlush(code(account, VerificationPurpose.EMAIL_VERIFY, "hash-verify-" + account));
        codes.saveAndFlush(code(account, VerificationPurpose.PASSWORD_RESET, "hash-reset-" + account));
        var change = code(account, VerificationPurpose.EMAIL_CHANGE, "hash-change-" + account);
        change.setTargetEmail("new-" + account + "@example.test");
        codes.saveAndFlush(change);
        codes.saveAndFlush(code(account, VerificationPurpose.DEPENDENT_ADD, "hash-dependent-" + account));

        for (var purpose : new VerificationPurpose[] {VerificationPurpose.EMAIL_VERIFY, VerificationPurpose.PASSWORD_RESET,
                VerificationPurpose.DEPENDENT_ADD}) {
            var duplicate = code(account, purpose, "hash-dup-" + purpose + "-" + account);
            assertThrows(DataIntegrityViolationException.class, () -> codes.saveAndFlush(duplicate),
                    "second open " + purpose + " code must be rejected");
        }
        var duplicateChange = code(account, VerificationPurpose.EMAIL_CHANGE, "hash-dup-change-" + account);
        duplicateChange.setTargetEmail("other-" + account + "@example.test");
        assertThrows(DataIntegrityViolationException.class, () -> codes.saveAndFlush(duplicateChange));
        assertEquals(4, jdbc.queryForObject("select count(*) from verification_codes where account_id = ?",
                Integer.class, account));
    }

    @Test
    void emailChangeRequiresATargetEmail() {
        UUID account = account();
        var missingTarget = code(account, VerificationPurpose.EMAIL_CHANGE, "hash-no-target-" + account);
        assertThrows(DataIntegrityViolationException.class, () -> codes.saveAndFlush(missingTarget));
    }

    @Test
    void supersedeOnlyAffectsTheSamePurposeAndFreesTheSlotForANewCode() {
        UUID account = account();
        UUID otherAccount = account();
        codes.saveAndFlush(code(account, VerificationPurpose.EMAIL_VERIFY, "hash-v1-" + account));
        codes.saveAndFlush(code(account, VerificationPurpose.PASSWORD_RESET, "hash-r1-" + account));
        codes.saveAndFlush(code(otherAccount, VerificationPurpose.EMAIL_VERIFY, "hash-v-other-" + otherAccount));

        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            // Same order the services use: supersede first, then insert, in one transaction.
            assertEquals(1, codes.supersedeCurrent(account, VerificationPurpose.EMAIL_VERIFY, Instant.now()));
            codes.save(code(account, VerificationPurpose.EMAIL_VERIFY, "hash-v2-" + account));
        });

        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where account_id = ? "
                + "and purpose = 'EMAIL_VERIFY' and superseded_at is not null", Integer.class, account));
        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where account_id = ? "
                + "and purpose = 'EMAIL_VERIFY' and superseded_at is null", Integer.class, account));
        assertEquals(0, jdbc.queryForObject("select count(*) from verification_codes where account_id = ? "
                + "and purpose = 'PASSWORD_RESET' and superseded_at is not null", Integer.class, account));
        assertEquals(0, jdbc.queryForObject("select count(*) from verification_codes where account_id = ? "
                + "and superseded_at is not null", Integer.class, otherAccount));
        var current = tx.execute(status -> codes.findCurrentForUpdate(account, VerificationPurpose.EMAIL_VERIFY)
                .map(VerificationCodeJpaEntity::getCodeHash).orElseThrow());
        assertEquals("hash-v2-" + account, current);
        assertTrue(tx.execute(status -> codes.findCurrentForUpdate(account, VerificationPurpose.EMAIL_CHANGE)).isEmpty());
    }

    @Test
    void passwordResetIsFoundByHashOnlyForItsOwnPurposeAndHashesAreUnique() {
        UUID account = account();
        UUID otherAccount = account();
        String hash = "reset-hash-" + UUID.randomUUID();
        codes.saveAndFlush(code(account, VerificationPurpose.PASSWORD_RESET, hash));
        codes.saveAndFlush(code(account, VerificationPurpose.EMAIL_VERIFY, "verify-hash-" + UUID.randomUUID()));

        assertEquals(account, codes.findAccountIdByCodeHash(hash, VerificationPurpose.PASSWORD_RESET).orElseThrow());
        assertTrue(codes.findAccountIdByCodeHash(hash, VerificationPurpose.EMAIL_VERIFY).isEmpty());
        assertTrue(codes.findByCodeHash(hash, VerificationPurpose.EMAIL_CHANGE).isEmpty());
        var locked = new TransactionTemplate(transactionManager).execute(status ->
                codes.findByCodeHashForUpdate(hash, VerificationPurpose.PASSWORD_RESET).map(VerificationCodeJpaEntity::getAccountId));
        assertEquals(account, locked.orElseThrow());

        var sameHash = code(otherAccount, VerificationPurpose.PASSWORD_RESET, hash);
        assertThrows(DataIntegrityViolationException.class, () -> codes.saveAndFlush(sameHash));
    }

    @Test
    void resendCountIsScopedByAccountAndPurpose() {
        UUID account = account();
        Instant before = Instant.now().minusSeconds(60);
        var verify = code(account, VerificationPurpose.EMAIL_VERIFY, "count-v-" + account);
        verify.setSupersededAt(Instant.now());
        codes.saveAndFlush(verify);
        codes.saveAndFlush(code(account, VerificationPurpose.EMAIL_VERIFY, "count-v2-" + account));
        codes.saveAndFlush(code(account, VerificationPurpose.PASSWORD_RESET, "count-r-" + account));

        assertEquals(2, codes.countByAccountIdAndPurposeAndCreatedAtAfter(account, VerificationPurpose.EMAIL_VERIFY, before));
        assertEquals(1, codes.countByAccountIdAndPurposeAndCreatedAtAfter(account, VerificationPurpose.PASSWORD_RESET, before));
        assertEquals(0, codes.countByAccountIdAndPurposeAndCreatedAtAfter(account, VerificationPurpose.EMAIL_CHANGE, before));
        assertEquals(0, codes.countByAccountIdAndPurposeAndCreatedAtAfter(account, VerificationPurpose.EMAIL_VERIFY,
                Instant.now().plusSeconds(60)));
    }

    private VerificationCodeJpaEntity code(UUID account, VerificationPurpose purpose, String hash) {
        var code = new VerificationCodeJpaEntity();
        Instant now = Instant.now();
        code.setAccountId(account);
        code.setPurpose(purpose);
        code.setCodeHash(hash);
        code.setCreatedAt(now);
        code.setExpiresAt(now.plusSeconds(600));
        return code;
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                + "values (?, ?, 'PATIENT', 'ACTIVE', now(), now())", id, EMAIL_PREFIX + id + "@example.test");
        return id;
    }
}
