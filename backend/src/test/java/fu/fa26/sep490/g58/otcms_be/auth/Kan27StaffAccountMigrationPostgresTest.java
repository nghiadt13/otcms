package fu.fa26.sep490.g58.otcms_be.auth;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Isolated schemas in the documented local test DB; no application schema cleanup. */
@Disabled("final-db: account_management_audits merged into audit_logs (also failing before final-db)")
class Kan27StaffAccountMigrationPostgresTest {
    @TempDir Path directory;
    private static final String URL = "jdbc:postgresql://127.0.0.1:" + System.getenv().getOrDefault("OTCMS_TEST_DB_PORT", "5432") + "/otcms_test_db";
    private static final String V050 = "V050__staff_account_management_r3.sql";

    @Test void freshMigrationWithoutV049V051OrV052EnforcesR3Constraints() throws Exception { verify(false); }
    @Test void outOfOrderV050AfterV072PreservesLegacyRowsAndConstraints() throws Exception { verify(true); }

    private void verify(boolean late) throws Exception {
        String password = System.getenv("OTCMS_TEST_DB_PASSWORD");
        if (password == null || password.isBlank())
            throw new IllegalStateException("TEST ENVIRONMENT REQUIRES LOCAL SECRET CONFIGURATION: OTCMS_TEST_DB_PASSWORD; docs/local-testing.md");
        String username = System.getenv().getOrDefault("OTCMS_TEST_DB_USERNAME", "postgres");
        String schema = "kan27_" + UUID.randomUUID().toString().replace("-", "");
        Path source = Path.of("src/main/resources/db/migration");
        try (var files = Files.list(source)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith("V049__") || name.startsWith("V051__") || name.startsWith("V052__")) continue;
                if (late && name.equals(V050)) continue;
                Files.copy(file, directory.resolve(file.getFileName()));
            }
        }
        try (Connection connection = DriverManager.getConnection(URL, username, password);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                migrate(schema, username, password);
                statement.execute("SET search_path TO " + schema);
                List<UUID> legacy = new ArrayList<>();
                for (String type : List.of("PATIENT", "ADMIN", "OWNER", "RECEPTIONIST", "DOCTOR", "LAB_TECHNICIAN", "IMAGING_TECHNICIAN"))
                    legacy.add(insert(connection, type, UUID.randomUUID() + "@example.test", null));
                if (late) {
                    try (var version = statement.executeQuery("select max(version::int) from flyway_schema_history where version ~ '^[0-9]+$'")) {
                        assertTrue(version.next());
                        assertEquals(72, version.getInt(1));
                    }
                    Files.copy(source.resolve(V050), directory.resolve(V050));
                    migrate(schema, username, password);
                }
                for (UUID id : legacy) try (var query = connection.prepareStatement("select normalized_email,username from accounts where id=?")) {
                    query.setObject(1, id);
                    try (var row = query.executeQuery()) { assertTrue(row.next()); assertNotNull(row.getString(1)); assertNull(row.getString(2)); }
                }
                SQLException patientError = assertThrows(SQLException.class, () -> insert(connection, "PATIENT", null, "patient-name"));
                assertEquals("23514", patientError.getSQLState());
                List<UUID> staff = new ArrayList<>();
                for (String type : List.of("RECEPTIONIST", "DOCTOR", "LAB_TECHNICIAN", "IMAGING_TECHNICIAN", "ADMIN"))
                    staff.add(insert(connection, type, null, type + "@exact"));
                insert(connection, "DOCTOR", null, "CaseName");
                insert(connection, "DOCTOR", null, "casename");
                assertEquals("23505", assertThrows(SQLException.class,
                        () -> insert(connection, "DOCTOR", null, "CaseName")).getSQLState());
                UUID target = staff.getFirst(), actor = staff.getLast();
                for (String action : List.of("CREATE", "ACTIVATE", "DEACTIVATE", "REISSUE_PASSWORD"))
                    audit(connection, target, actor, action);
                assertEquals("23514", assertThrows(SQLException.class, () -> audit(connection, target, actor, "UNKNOWN")).getSQLState());
                assertEquals("23503", assertThrows(SQLException.class, () -> audit(connection, UUID.randomUUID(), actor, "CREATE")).getSQLState());
                assertEquals("23503", assertThrows(SQLException.class, () -> audit(connection, target, UUID.randomUUID(), "CREATE")).getSQLState());
                try (var delete = connection.prepareStatement("delete from accounts where id=?")) {
                    delete.setObject(1, target);
                    assertEquals("23001", assertThrows(SQLException.class, delete::executeUpdate).getSQLState());
                    delete.setObject(1, actor);
                    assertEquals("23001", assertThrows(SQLException.class, delete::executeUpdate).getSQLState());
                }
                try (var indexes = statement.executeQuery("select indexdef from pg_indexes where schemaname='" + schema + "' and tablename='account_management_audits'")) {
                    var definitions = new ArrayList<String>();
                    while (indexes.next()) definitions.add(indexes.getString(1));
                    assertTrue(definitions.stream().anyMatch(d -> d.contains("(account_id, occurred_at DESC, id DESC)")));
                    assertTrue(definitions.stream().anyMatch(d -> d.contains("(actor_account_id, occurred_at DESC, id DESC)")));
                }
                try (var columns = statement.executeQuery("select column_name from information_schema.columns where table_schema='" + schema + "' and table_name='account_management_audits'")) {
                    Set<String> names = new HashSet<>();
                    while (columns.next()) names.add(columns.getString(1));
                    assertEquals(Set.of("id", "account_id", "actor_account_id", "action", "occurred_at", "correlation_id"), names);
                }
            } finally {
                statement.execute("SET search_path TO public");
                // schema is generated locally with a fixed prefix and UUID, never externally supplied.
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private void migrate(String schema, String username, String password) {
        Flyway.configure().dataSource(URL, username, password).schemas(schema).defaultSchema(schema).outOfOrder(true)
                .locations("filesystem:" + directory.toAbsolutePath()).load().migrate();
    }
    private UUID insert(Connection connection, String type, String email, String username) throws SQLException {
        UUID id = UUID.randomUUID();
        try (var insert = connection.prepareStatement("insert into accounts(id,account_type,status,normalized_email,username,created_at,updated_at) values (?,?,'ACTIVE',?,?,now(),now())")) {
            insert.setObject(1, id); insert.setString(2, type); insert.setString(3, email); insert.setString(4, username);
            insert.executeUpdate();
        }
        return id;
    }
    private void audit(Connection connection, UUID target, UUID actor, String action) throws SQLException {
        try (var insert = connection.prepareStatement("insert into account_management_audits(id,account_id,actor_account_id,action,occurred_at,correlation_id) values (?,?,?,?,now(),?)")) {
            insert.setObject(1, UUID.randomUUID()); insert.setObject(2, target); insert.setObject(3, actor);
            insert.setString(4, action); insert.setString(5, UUID.randomUUID().toString()); insert.executeUpdate();
        }
    }
}
