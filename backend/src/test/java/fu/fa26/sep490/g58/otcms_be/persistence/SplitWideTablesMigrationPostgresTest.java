package fu.fa26.sep490.g58.otcms_be.persistence;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.util.StreamUtils;

/**
 * V137-V142 (split of the wide tables): builds a schema at V136, loads sample rows that use every moved column
 * (src/test/resources/db/split-wide-tables-seed-v136.sql), migrates to the latest version and checks that nothing was
 * lost. The seed uses fake foreign-key targets, so the seed session and Flyway run with FK triggers disabled
 * (session_replication_role = replica; needs the superuser of the local test database).
 */
@SpringBootTest
@LocalPostgresTest
class SplitWideTablesMigrationPostgresTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void movesEveryColumnGroupWithoutLosingRows() throws Exception {
        String schema = "split_wide_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        // one dedicated connection (not the pool): the session settings below must not leak into other tests
        Connection own = dataSource.getConnection();
        try {
            var single = new SingleConnectionDataSource(own, true);
            Flyway.configure().dataSource(single).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").target(MigrationVersion.fromVersion("136")).load().migrate();
            seed(own, schema);
            Flyway.configure().dataSource(single).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").initSql("set session_replication_role = replica")
                    .target(MigrationVersion.LATEST).load().migrate();

            // V137: every key moved, with the owner / resource the code relies on
            assertEquals(11, count(schema, "idempotency_records"));
            assertEquals(1, count(schema, "idempotency_records where scope = 'APPOINTMENT_CHECKIN' and owner_id = '00000000-0000-0000-0000-0000000000c2' and idempotency_key = 'checkin-key-1' and resource_id = '00000000-0000-0000-0000-0000000000d2'"));
            assertEquals(1, count(schema, "idempotency_records where scope = 'ORDER_ROUND_SUBMIT' and owner_id = '00000000-0000-0000-0000-0000000000c3' and resource_id = '00000000-0000-0000-0000-000000000201'"));
            assertEquals(1, count(schema, "idempotency_records where scope = 'RESULT_DRAFT' and owner_id = '00000000-0000-0000-0000-000000000104' and resource_id = '00000000-0000-0000-0000-000000000501'"));
            assertEquals(1, count(schema, "idempotency_records where scope = 'IMAGING_UPLOAD' and idempotency_key = '00000000-0000-0000-0000-000000000104:00000000-0000-0000-0000-000000000302:upload-key-1'"));
            assertEquals(1, count(schema, "idempotency_records where scope = 'IMAGING_SEND' and owner_id = resource_id and idempotency_key = 'send-key-1'"));
            assertEquals(2, count(schema, "idempotency_records where scope = 'PAYMENT_ATTEMPT'"));

            // V138: one row per round; orders keep only the round id
            assertEquals(2, count(schema, "clinical_ordering_rounds"));
            assertEquals(2, jdbc.queryForObject("select round_number from " + schema + ".clinical_ordering_rounds where id = '00000000-0000-0000-0000-000000000202'", Integer.class));

            // V139: executions for the 3 imaging orders with an execution; the lab order keeps its room on the order row
            assertEquals(3, count(schema, "imaging_order_executions"));
            assertEquals(3, jdbc.queryForObject("select version from " + schema + ".imaging_order_executions where service_order_id = '00000000-0000-0000-0000-000000000103'", Integer.class));
            assertEquals("STUDY-1", jdbc.queryForObject("select study_ref from " + schema + ".imaging_order_executions where service_order_id = '00000000-0000-0000-0000-000000000104'", String.class));
            assertEquals(1, count(schema, "clinical_service_orders where service_type = 'LABORATORY' and room_id is not null and lab_status = 'ASSIGNED'"));

            // V140: only the study that has release data
            assertEquals(1, count(schema, "imaging_study_releases where imaging_study_id = '00000000-0000-0000-0000-000000000601' and confirmation_attempt_count = 2 and last_safe_error_code = 'SOME_ERROR'"));
            assertEquals(2, count(schema, "imaging_studies"));

            // V141: the QR arrival and the counter check-in, none for the plain appointment
            assertEquals(2, count(schema, "appointment_arrivals"));
            assertEquals(1, count(schema, "appointment_arrivals where arrival_source = 'PATIENT_QR' and checkin_actor_account_id is null"));
            assertEquals(1, count(schema, "appointment_arrivals where arrived_at is null and checkin_actor_account_id is not null"));

            // V142: PayOS link fields in one jsonb; the counter payment has none
            assertEquals("https://pay.example/checkout", jdbc.queryForObject("select provider_payload ->> 'checkoutUrl' from " + schema + ".payment_attempts where id = '00000000-0000-0000-0000-000000000801'", String.class));
            assertEquals(1, count(schema, "payment_attempts where provider_payload is null"));

            // the wide tables really got narrower
            assertTrue(columns(schema, "appointments") <= 21);
            assertTrue(columns(schema, "imaging_studies") <= 23);
            assertTrue(columns(schema, "payment_attempts") <= 18);
        } finally {
            try (Statement reset = own.createStatement()) {
                reset.execute("reset session_replication_role");
                reset.execute("reset search_path");
            }
            own.close();
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private void seed(Connection connection, String schema) throws Exception {
        String sql = StreamUtils.copyToString(new ClassPathResource("db/split-wide-tables-seed-v136.sql").getInputStream(), StandardCharsets.UTF_8);
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            statement.execute("set session_replication_role = replica");
            statement.execute(sql);
        }
    }

    private int count(String schema, String fromAndWhere) {
        return jdbc.queryForObject("select count(*) from " + schema + "." + fromAndWhere, Integer.class);
    }

    private int columns(String schema, String table) {
        return jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = ? and table_name = ?", Integer.class, schema, table);
    }
}
