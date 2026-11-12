package fu.fa26.sep490.g58.otcms_be.content;
import fu.fa26.sep490.g58.otcms_be.content.repository.*;
import fu.fa26.sep490.g58.otcms_be.content.exception.*;
import fu.fa26.sep490.g58.otcms_be.content.service.impl.*;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Exercises the team's out-of-order Flyway policy against a dedicated loopback DB only. */
class ContentAuditMigrationOrderTest {
    @TempDir Path directory;
    private static final String URL="jdbc:postgresql://127.0.0.1:" + System.getenv().getOrDefault("OTCMS_TEST_DB_PORT", "5432") + "/otcms_test_db";
    @Test void contentFirstThenActivityLogIncludesEveryContentEventExactlyOnce() throws Exception {
        check(true);
    }
    @Test void normalOrderIncludesEveryContentEventExactlyOnce() throws Exception {
        check(false);
    }
    private void check(boolean lateView) throws Exception {
        Path source=Path.of("src/main/resources/db/migration");
        Path view=source.resolve("V070__activity_log_read_projection.sql");
        assumeTrue(Files.exists(view),"KAN-40 must be present to verify the combined migration order");
        String password=System.getenv("OTCMS_TEST_DB_PASSWORD");
        if(password==null) throw new IllegalStateException("Local test DB password is required");
        String username=System.getenv().getOrDefault("OTCMS_TEST_DB_USERNAME","postgres");
        String schema="content_order_"+UUID.randomUUID().toString().replace("-","");
        try(Connection connection=DriverManager.getConnection(URL,username,password);Statement statement=connection.createStatement()) {
            statement.execute("CREATE SCHEMA "+schema);
            try {
                try(var files=Files.list(source)) {
                    for(Path file:files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                        if(lateView && file.getFileName().equals(view.getFileName())) continue;
                        Files.copy(file,directory.resolve(file.getFileName()));
                    }
                }
                migrate(schema,username,password);
                if(lateView) {
                    Files.copy(view,directory.resolve(view.getFileName()));
                    migrate(schema,username,password);
                }
                UUID actor=UUID.randomUUID();
                statement.execute("SET search_path TO "+schema);
                try(var insert=connection.prepareStatement("INSERT INTO accounts(id,normalized_email,account_type,status,created_at,updated_at) VALUES(?,?,'ADMIN','ACTIVE',now(),now())")) {
                    insert.setObject(1,actor);
                    insert.setString(2,actor+"@order.test");
                    insert.executeUpdate();
                }
                try(var insert=connection.prepareStatement("INSERT INTO content_audits(id,actor_account_id,category,action,record_id,record_label,occurred_at) VALUES(?,?,'CONTENT','UPDATE_CONTENT',?,'Safe label',now())")) {
                    insert.setObject(1,UUID.randomUUID());
                    insert.setObject(2,actor);
                    insert.setObject(3,UUID.randomUUID());
                    insert.executeUpdate();
                }
                try(var result=statement.executeQuery("SELECT count(*) FROM activity_log_entries WHERE category='CONTENT'")) {
                    assertTrue(result.next());
                    assertEquals(1,result.getInt(1));
                }
            }
            finally {
                statement.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
            }
        }
    }
    private void migrate(String schema,String username,String password) {
        // This historical test verifies the legacy V070/V103 content projection, replaced by V109.
        Flyway.configure().dataSource(URL,username,password).schemas(schema).defaultSchema(schema).outOfOrder(true).target("108")
        .locations("filesystem:"+directory.toAbsolutePath()).load().migrate();
    }
}
