package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.exception.EmailChangeWriteException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.EmailChangeWriteException.Reason;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** Updates email through a database routine that returns safe collision outcomes without logging the address. */
@Repository
public class EmailChangeAccountWriter {
    private final AccountRepository accounts;
    private final EntityManager entityManager;

    public EmailChangeAccountWriter(AccountRepository accounts, EntityManager entityManager) {
        this.accounts = accounts;
        this.entityManager = entityManager;
    }

    public void updateVerifiedEmail(AccountJpaEntity account, String target, Instant verifiedAt) {
        try {
            String result = accounts.updateVerifiedEmail(account.getId(), target, verifiedAt);
            if ("COLLISION".equals(result)) {
                throw new EmailChangeWriteException(Reason.COLLISION);
            }
            if (!"UPDATED".equals(result)) {
                throw new EmailChangeWriteException(Reason.OPERATION_FAILED);
            }
        } catch (DataIntegrityViolationException failure) {
            throw new EmailChangeWriteException(Reason.COLLISION);
        } catch (EmailChangeWriteException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EmailChangeWriteException(Reason.OPERATION_FAILED);
        }
        // Only this account is detached. The managed challenge can still flush atomically, and
        // OpenEntityManagerInView cannot return the old email to the subsequent account projection.
        entityManager.detach(account);
    }
}
