package fu.fa26.sep490.g58.otcms_be.persistence;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@LocalPostgresTest
class T017PersistenceCheckpointPostgresIntegrationTest {
    private static final String V001_SHA256 =
            "9a616dcb1d8ffbf007d6af3e8fd1fa3f1eb97c60294e1d5d5fe7ba37802d35a6";

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void cleanSchemaMigratesV001ThroughV014WithFinalOwnerConstraints() {
        String schema = createSchema("t017_clean_");
        try {
            Flyway flyway = flyway(schema, "14");
            assertEquals(16, flyway.migrate().migrationsExecuted);
            assertEquals("014", flyway.info().current().getVersion().getVersion());
            assertEquals(16, jdbc.queryForObject("select count(*) from " + schema + ".flyway_schema_history where success", Integer.class));

            assertTable(schema, "appointments");
            assertTable(schema, "patient_profiles");
            assertTable(schema, "clinic_services");
            assertTable(schema, "doctor_profiles");
            assertTable(schema, "clinic_visits");
            assertTable(schema, "visit_audits");
            assertTable(schema, "integration_coordination_guard");
            assertConstraint(schema, "uk_patient_profiles_account_id", "patient_profiles", "u", "unique (account_id)");
            assertConstraint(schema, "fk_doctor_service_assignments_doctor_profile", "doctor_service_assignments", "f",
                    "foreign key (doctor_profile_id) references doctor_profiles(id)");
            assertConstraint(schema, "fk_clinic_visits_prior_appointment_patient", "clinic_visits", "f",
                    "foreign key (prior_appointment_id, patient_profile_id) references appointments(id, patient_profile_id)");
            assertConstraint(schema, "fk_appointments_doctor_service_assignment", "appointments", "f",
                    "foreign key (doctor_profile_id, service_id) references doctor_service_assignments(doctor_profile_id, service_id)");
            assertConstraint(schema, "ex_doctor_work_shift_no_overlap", "doctor_work_shifts", "x",
                    "exclude using gist (doctor_profile_id with =, tstzrange(starts_at, ends_at, '[)'::text) with &&)");
            assertConstraint(schema, "ex_appointments_doctor_occupied_range", "appointments", "x",
                    "exclude using gist (doctor_profile_id with =, tstzrange(start_time, occupied_until, '[)'::text) with &&)");
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void upgradeFromV001PreservesAuthAndPatientDataAndTheOriginalChecksum() throws Exception {
        assertEquals(V001_SHA256, v001Sha256());
        String schema = createSchema("t017_upgrade_");
        try {
            Flyway v001 = flyway(schema, "1");
            assertEquals(1, v001.migrate().migrationsExecuted);
            Integer checksumBefore = migrationChecksum(schema, "001");

            UUID accountId = UUID.randomUUID();
            UUID identityId = UUID.randomUUID();
            UUID consentId = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-10-01T08:30:00Z");
            Instant linkedAt = Instant.parse("2026-10-02T09:15:00Z");
            Instant acceptedAt = Instant.parse("2026-10-03T10:45:00Z");
            jdbc.update("insert into " + schema + ".accounts (id, normalized_email, account_type, status, password_hash, email_verified_at, failed_credential_count, created_at, updated_at) values (?, ?, 'PATIENT', 'ACTIVE', ?, ?, 2, ?, ?)",
                    accountId, "t017-preserved@example.test", "$2a$10$preserved-hash",
                    Timestamp.from(createdAt), Timestamp.from(createdAt), Timestamp.from(createdAt));
            jdbc.update("insert into " + schema + ".patient_profiles (account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, ?, 'PATIENT', ?, 'OTHER')",
                    accountId, "T017 Preserved Patient", "+84912345678", java.sql.Date.valueOf("1991-02-03"));
            jdbc.update("insert into " + schema + ".external_identities (id, account_id, provider, provider_subject, normalized_email_at_link, linked_at) values (?, ?, 'GOOGLE', ?, ?, ?)",
                    identityId, accountId, "t017-google-subject", "t017-preserved@example.test", Timestamp.from(linkedAt));
            jdbc.update("insert into " + schema + ".consent_evidence (id, account_id, actor_type, terms_version, privacy_version, accepted_at) values (?, ?, 'PATIENT', 'terms-t017', 'privacy-t017', ?)",
                    consentId, accountId, Timestamp.from(acceptedAt));

            Flyway upgraded = flyway(schema, "14");
            assertEquals(15, upgraded.migrate().migrationsExecuted);
            assertEquals("014", upgraded.info().current().getVersion().getVersion());
            assertEquals(checksumBefore, migrationChecksum(schema, "001"));
            assertEquals(16, jdbc.queryForObject("select count(*) from " + schema + ".flyway_schema_history where success", Integer.class));

            assertEquals("PATIENT", jdbc.queryForObject("select account_type from " + schema + ".accounts where id = ?", String.class, accountId));
            assertEquals("ACTIVE", jdbc.queryForObject("select status from " + schema + ".accounts where id = ?", String.class, accountId));
            assertEquals("$2a$10$preserved-hash", jdbc.queryForObject("select password_hash from " + schema + ".accounts where id = ?", String.class, accountId));
            assertEquals(Timestamp.from(createdAt), jdbc.queryForObject("select created_at from " + schema + ".accounts where id = ?", Timestamp.class, accountId));

            UUID profileId = jdbc.queryForObject("select id from " + schema + ".patient_profiles where account_id = ?", UUID.class, accountId);
            assertNotNull(profileId);
            assertEquals("T017 Preserved Patient", jdbc.queryForObject("select full_name from " + schema + ".patient_profiles where id = ?", String.class, profileId));
            assertEquals("+84912345678", jdbc.queryForObject("select phone_canonical from " + schema + ".patient_profiles where id = ?", String.class, profileId));
            assertEquals(java.sql.Date.valueOf("1991-02-03"), jdbc.queryForObject("select date_of_birth from " + schema + ".patient_profiles where id = ?", java.sql.Date.class, profileId));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".external_identities where id = ? and account_id = ? and provider_subject = ? and linked_at = ?",
                    Integer.class, identityId, accountId, "t017-google-subject", Timestamp.from(linkedAt)));
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema + ".consent_evidence where id = ? and account_id = ? and accepted_at = ?",
                    Integer.class, consentId, accountId, Timestamp.from(acceptedAt)));
            assertConstraint(schema, "fk_appointments_doctor_service_assignment", "appointments", "f",
                    "foreign key (doctor_profile_id, service_id) references doctor_service_assignments(doctor_profile_id, service_id)");
            assertConstraint(schema, "ex_appointments_doctor_occupied_range", "appointments", "x",
                    "exclude using gist (doctor_profile_id with =, tstzrange(start_time, occupied_until, '[)'::text) with &&)");
            assertTable(schema, "visit_audits");
        } finally {
            dropSchema(schema);
        }
    }

    private Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private String createSchema(String prefix) {
        String schema = prefix + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        return schema;
    }

    private void dropSchema(String schema) {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    private Integer migrationChecksum(String schema, String version) {
        return jdbc.queryForObject("select checksum from " + schema + ".flyway_schema_history where version = ?",
                Integer.class, version);
    }

    private void assertTable(String schema, String table) {
        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = ?",
                Integer.class, schema, table));
    }

    private void assertConstraint(String schema, String constraint, String table, String type, String definitionFragment) {
        var actual = jdbc.queryForList("select t.relname as table_name, c.contype::text as constraint_type, "
                        + "lower(pg_get_constraintdef(c.oid)) as definition from pg_constraint c "
                        + "join pg_class t on t.oid = c.conrelid join pg_namespace n on n.oid = t.relnamespace "
                        + "where n.nspname = ? and c.conname = ?", schema, constraint);
        assertEquals(1, actual.size(), "Expected one schema-local constraint named " + constraint);
        assertEquals(table, actual.getFirst().get("table_name"));
        assertEquals(type, actual.getFirst().get("constraint_type"));
        String rawDefinition = ((String) actual.getFirst().get("definition")).replaceAll("\\s+", " ");
        if ("f".equals(type)) {
            assertTrue(rawDefinition.contains(" references " + schema + "."),
                    () -> constraint + " must reference a table in schema " + schema + ": " + rawDefinition);
        }
        String definition = rawDefinition.replace(schema + ".", "");
        assertTrue(definition.contains(definitionFragment), () -> constraint + " definition was: " + definition);
    }

    private String v001Sha256() throws IOException, NoSuchAlgorithmException {
        try (InputStream migration = getClass().getClassLoader().getResourceAsStream("db/migration/V001__auth_schema.sql")) {
            assertNotNull(migration, "V001 migration must be available on the test classpath");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(migration.readAllBytes()));
        }
    }
}
