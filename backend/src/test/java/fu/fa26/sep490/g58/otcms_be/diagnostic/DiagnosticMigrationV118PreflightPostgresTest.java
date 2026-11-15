package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@LocalPostgresTest
class DiagnosticMigrationV118PreflightPostgresTest {
    @Autowired JdbcTemplate jdbc;

    @Test void requestWithoutOrderAbortsMigrationBeforeChanges() throws Exception {
        assertPreflightRejects(null);
    }

    @Test void requestReferencingMissingOrderAbortsMigrationBeforeChanges() throws Exception {
        assertPreflightRejects(UUID.randomUUID());
    }

    @Test void multipleRequestsForOneOrderAbortMigrationBeforeChanges() throws Exception {
        assertPreflightRejects(UUID.randomUUID(), true);
    }

    private void assertPreflightRejects(UUID orderId) throws Exception {
        assertPreflightRejects(orderId, false);
    }

    private void assertPreflightRejects(UUID orderId, boolean duplicate) throws Exception {
        String sql = new ClassPathResource("db/migration/V118__final_db_diagnostic_imaging_orders.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        int start = sql.indexOf("DO $$");
        int end = sql.indexOf("END $$;", start);
        assertTrue(start >= 0 && end > start, "V118 must retain its preflight block");
        String preflight = sql.substring(start, end + "END $$;".length());
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE diagnostic_requests (id uuid, service_order_id uuid) ON COMMIT DROP");
                statement.execute("CREATE TEMP TABLE clinical_service_orders (id uuid) ON COMMIT DROP");
                if (duplicate) {
                    try (var insertOrder = connection.prepareStatement("INSERT INTO clinical_service_orders VALUES (?)")) {
                        insertOrder.setObject(1, orderId);
                        insertOrder.executeUpdate();
                    }
                }
                try (var insert = connection.prepareStatement("INSERT INTO diagnostic_requests VALUES (?, ?)")) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setObject(2, orderId);
                    insert.executeUpdate();
                    if (duplicate) {
                        insert.setObject(1, UUID.randomUUID());
                        insert.executeUpdate();
                    }
                }
                SQLException error = assertThrows(SQLException.class, () -> statement.execute(preflight));
                assertTrue(error.getMessage().contains(duplicate ? "more than one diagnostic request per clinical service order"
                        : "diagnostic request without an existing clinical service order"));
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
    }
}
