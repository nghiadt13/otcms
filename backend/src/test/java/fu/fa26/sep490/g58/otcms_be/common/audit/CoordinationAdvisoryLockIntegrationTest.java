package fu.fa26.sep490.g58.otcms_be.common.audit;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@LocalPostgresTest
class CoordinationAdvisoryLockIntegrationTest {
    @Autowired CoordinationGuardService guard;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void excludesOtherTransactionsAndReleasesOnCommitAndRollback() throws Exception {
        for (boolean rollback : new boolean[] { false, true }) {
            try (Connection contender = dataSource.getConnection(); var pool = Executors.newSingleThreadExecutor()) {
                contender.setAutoCommit(false);
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    guard.acquire();
                    assertEquals("2s", jdbc.queryForObject("select current_setting('lock_timeout')", String.class));
                    try {
                        assertFalse(pool.submit(() -> tryLock(contender)).get(5, TimeUnit.SECONDS));
                    } catch (Exception exception) { throw new AssertionError(exception); }
                    if (rollback) status.setRollbackOnly();
                });
                assertTrue(tryLock(contender));
                contender.rollback();
            }
        }
    }

    @Test
    void lockWaitTimesOutAndGuardRequiresTransaction() throws Exception {
        assertThrows(IllegalTransactionStateException.class, guard::acquire);
        try (Connection blocker = dataSource.getConnection(); var pool = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            assertTrue(tryLock(blocker));
            try {
                var failure = pool.submit(() -> assertThrows(RuntimeException.class,
                        () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> guard.acquire())))
                        .get(8, TimeUnit.SECONDS);
                Throwable cause = failure;
                while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
                assertInstanceOf(SQLException.class, cause);
                assertEquals("55P03", ((SQLException) cause).getSQLState());
            } finally { blocker.rollback(); }
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> guard.acquire());
    }

    private boolean tryLock(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("select pg_try_advisory_xact_lock(4903490001)")) {
            result.next();
            return result.getBoolean(1);
        }
    }
}
