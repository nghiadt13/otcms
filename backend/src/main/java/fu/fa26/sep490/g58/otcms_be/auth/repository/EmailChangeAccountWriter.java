package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.exception.EmailChangeWriteException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.EmailChangeWriteException.Reason;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Uses the current JPA transaction's JDBC connection without Hibernate's SQL error logger. */
@Repository
public class EmailChangeAccountWriter {
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public EmailChangeAccountWriter(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    public void updateVerifiedEmail(AccountJpaEntity account, String target, Instant verifiedAt) {
        try {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (var statement = connection.prepareStatement("update accounts set normalized_email = ?, "
                        + "email_verified_at = ?, updated_at = ? where id = ?")) {
                    statement.setString(1, target);
                    statement.setTimestamp(2, Timestamp.from(verifiedAt));
                    statement.setTimestamp(3, Timestamp.from(verifiedAt));
                    statement.setObject(4, account.getId());
                    if (statement.executeUpdate() != 1) throw new EmailChangeWriteException(Reason.OPERATION_FAILED);
                    return null;
                } catch (SQLException failure) {
                    // Catch before JdbcTemplate/Hibernate translation; do not retain or log raw diagnostics.
                    throw new EmailChangeWriteException("23505".equals(failure.getSQLState())
                            ? Reason.COLLISION : Reason.OPERATION_FAILED);
                }
            });
        } catch (DataAccessException failure) {
            throw new EmailChangeWriteException(Reason.OPERATION_FAILED);
        }
        // Only this account is detached. The managed challenge can still flush atomically, and
        // OpenEntityManagerInView cannot return the old email to the subsequent account projection.
        entityManager.detach(account);
    }
}
