package fu.fa26.sep490.g58.otcms_be.auth;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Kan29MyAccountMigrationPostgresTest {
    @TempDir Path directory;
    static final String URL = "jdbc:postgresql://127.0.0.1:5432/otcms_test_db";
    static final String MIGRATION = "V052__my_account_r3.sql";
    @Test void freshMigrationEnforcesAvatarAndChallengeConstraints() throws Exception { verify(false); }
    @Test void upgradeFromProductPreservesV048ThroughV051History() throws Exception { verify(true); }

    void verify(boolean upgrade) throws Exception {
        String password = System.getenv("OTCMS_TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("OTCMS_TEST_DB_PASSWORD must be configured");
        String user = System.getenv().getOrDefault("OTCMS_TEST_DB_USERNAME","postgres");
        String schema = "kan29_migration_" + UUID.randomUUID().toString().replace("-", "");
        Path source = Path.of("src/main/resources/db/migration");
        try (var files = Files.list(source)) {
            for (var file : files.filter(p -> p.toString().endsWith(".sql")).toList())
                if (!upgrade || !file.getFileName().toString().equals(MIGRATION)) Files.copy(file,directory.resolve(file.getFileName()));
        }
        migrate(schema,user,password);
        try (var c=DriverManager.getConnection(URL,user,password); var s=c.createStatement()) {
            s.execute("set search_path to " + schema);
            Map<String,Integer> history = history(s);
            UUID account = UUID.randomUUID();
            try (var insert=c.prepareStatement("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'PATIENT','ACTIVE',now(),now())")) {
                insert.setObject(1,account); insert.setString(2,account+"@example.test"); insert.executeUpdate();
            }
            if (upgrade) {
                assertEquals(0,scalar(s,"select count(*) from flyway_schema_history where version='052'"));
                Files.copy(source.resolve(MIGRATION),directory.resolve(MIGRATION));
                migrate(schema,user,password);
                assertEquals(history,history(s));
                assertEquals(1,scalar(s,"select count(*) from accounts where id='"+account+"'"));
            }
            assertEquals(1,scalar(s,"select count(*) from flyway_schema_history where version='052' and success"));
            assertEquals(Set.of("048","049","050","051"), history.keySet());
            try(var avatar=c.prepareStatement("insert into account_avatars(account_id,photo_content,photo_content_type,updated_at) values (?,?,?,now())")) {
                avatar.setObject(1,UUID.randomUUID()); avatar.setBytes(2,new byte[]{1}); avatar.setString(3,"image/png");
                assertEquals("23503",assertThrows(SQLException.class,avatar::executeUpdate).getSQLState());
                avatar.setObject(1,account); avatar.setString(3,"text/html");
                assertEquals("23514",assertThrows(SQLException.class,avatar::executeUpdate).getSQLState());
                avatar.setString(3,"image/png"); avatar.setBytes(2,new byte[0]);
                assertEquals("23514",assertThrows(SQLException.class,avatar::executeUpdate).getSQLState());
                avatar.setBytes(2,new byte[2097153]);
                assertEquals("23514",assertThrows(SQLException.class,avatar::executeUpdate).getSQLState());
                avatar.setBytes(2,new byte[2097152]); assertEquals(1,avatar.executeUpdate());
            }
            insertChallenge(c,account,"new@example.test",0);
            assertEquals("23505",assertThrows(SQLException.class,() -> insertChallenge(c,account,"other@example.test",0)).getSQLState());
            assertEquals("23503",assertThrows(SQLException.class,() -> insertChallenge(c,UUID.randomUUID(),"other@example.test",0)).getSQLState());
            s.execute("update email_change_challenges set superseded_at=now() where account_id='"+account+"'");
            assertEquals("23514",assertThrows(SQLException.class,() -> insertChallenge(c,account,"Mixed@EXAMPLE.TEST",0)).getSQLState());
            assertEquals("23514",assertThrows(SQLException.class,() -> insertChallenge(c,account,"other@example.test",6)).getSQLState());
            insertChallenge(c,account,"other@example.test",0);
            assertEquals(1,scalar(s,"select count(*) from pg_indexes where schemaname='"+schema+"' and indexname='uk_email_change_current' and indexdef like '%WHERE%consumed_at%NULL%superseded_at%NULL%'"));
            assertEquals(1,scalar(s,"select count(*) from pg_indexes where schemaname='"+schema+"' and indexname='ix_email_change_account_created'"));
        }
        // Generated test schemas are retained as inspectable evidence, never shared application data.
    }
    void migrate(String schema,String user,String password) {
        Flyway.configure().dataSource(URL,user,password).schemas(schema).defaultSchema(schema).outOfOrder(true)
                .locations("filesystem:"+directory.toAbsolutePath()).load().migrate();
    }
    Map<String,Integer> history(Statement s) throws Exception {
        var result=new HashMap<String,Integer>();
        try(var rows=s.executeQuery("select version,checksum from flyway_schema_history where version in ('048','049','050','051')")) {
            while(rows.next()) result.put(rows.getString(1),rows.getInt(2));
        }
        return result;
    }
    int scalar(Statement s,String sql) throws Exception { try(var rows=s.executeQuery(sql)) { assertTrue(rows.next()); return rows.getInt(1); } }
    void insertChallenge(Connection c,UUID account,String target,int attempts) throws Exception {
        try(var insert=c.prepareStatement("insert into email_change_challenges(id,account_id,normalized_target_email,otp_hash,expires_at,failed_attempts,created_at) values (?,?,?,'hash',now()+interval '10 minutes',?,now())")) {
            insert.setObject(1,UUID.randomUUID()); insert.setObject(2,account); insert.setString(3,target); insert.setInt(4,attempts); insert.executeUpdate();
        }
    }
}
