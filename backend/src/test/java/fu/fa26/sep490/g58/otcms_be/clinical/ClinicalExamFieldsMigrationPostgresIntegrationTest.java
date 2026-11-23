package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.util.LinkedHashMap;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V136 on real schemas. Upgrading from V134 maps the side labels that the pre-N2 form wrote to BR-39 codes;
 * a freshly migrated schema has the new columns (no JSON column) and rejects sides and directions outside the rules.
 */
@SpringBootTest
@LocalPostgresTest
class ClinicalExamFieldsMigrationPostgresIntegrationTest {
    /** Giá trị bên đã ghi trước N2 (nhãn, chuỗi rỗng "Không ghi", chữ tự do) và mã BR-39 mong đợi sau V136. */
    private static final String[][] LEGACY_SIDES = {
            {"Trái", "LEFT"}, {"Phải", "RIGHT"}, {"Hai bên", "BOTH"}, {" Trái ", "LEFT"},
            {"LEFT", "LEFT"}, {"Không áp dụng", "NOT_APPLICABLE"}, {"", null}, {"Chưa rõ", null}};

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @Test
    void upgradeMapsLegacySideLabelsToCodesAndAddsDescriptionAndDirections() {
        String schema = schemaName("upgrade");
        jdbc.execute("create schema " + schema);
        try {
            // The repository has no V135; 134 is the last migration before V136.
            flyway(schema, "134").migrate();
            var expected = new LinkedHashMap<UUID, String>();
            for (String[] row : LEGACY_SIDES) {
                UUID id = UUID.randomUUID();
                jdbc.update("insert into " + schema + ".clinical_examinations (id, visit_id, status, created_at, updated_at, injury_side) "
                        + "values (?, ?, 'DRAFT', now(), now(), ?)", id, UUID.randomUUID(), row[0]);
                expected.put(id, row[1]);
            }

            flyway(schema, null).migrate();

            for (var entry : expected.entrySet()) {
                assertEquals(entry.getValue(), jdbc.queryForObject("select injury_side from " + schema
                        + ".examinations where id = ?", String.class, entry.getKey()), entry.getKey().toString());
            }
            assertEquals(1, jdbc.queryForObject("select count(*) from " + schema
                    + ".flyway_schema_history where version = '136' and success", Integer.class));
            assertEquals("text", columnType(schema, "injury_description"));
            assertEquals("ARRAY", columnType(schema, "treatment_directions"));
            assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = ? "
                    + "and table_name = 'examinations' and column_name = 'injury_type'", Integer.class, schema));
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void constraintsRejectSidesOutsideBr39AndDirectionsOutsideBr40() {
        String schema = schemaName("constraints");
        jdbc.execute("create schema " + schema);
        try {
            flyway(schema, null).migrate();

            accepted(schema, "NULL", "NULL");
            accepted(schema, "'LEFT'", "NULL");
            accepted(schema, "NULL", "ARRAY['MEDICATION']");
            accepted(schema, "'NOT_APPLICABLE'", "ARRAY['HOME_CARE','MEDICATION','PHYSICAL_THERAPY','PROCEDURE_ELSEWHERE']::text[]");
            rejected(schema, "'Trái'", "NULL");
            rejected(schema, "'left'", "NULL");
            rejected(schema, "''", "NULL");
            rejected(schema, "NULL", "ARRAY[]::text[]");
            rejected(schema, "NULL", "ARRAY['HOME_CARE','HOME_CARE']");
            rejected(schema, "NULL", "ARRAY['OTHER']");
            rejected(schema, "NULL", "ARRAY['HOME_CARE', NULL]::text[]");
            rejected(schema, "NULL", "ARRAY[NULL]::text[]");
        } finally {
            jdbc.execute("drop schema if exists " + schema + " cascade");
        }
    }

    private void accepted(String schema, String side, String directions) {
        assertEquals(1, jdbc.update(insertSql(schema, side, directions)), insertSql(schema, side, directions));
    }

    private void rejected(String schema, String side, String directions) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insertSql(schema, side, directions)),
                insertSql(schema, side, directions));
    }

    private static String insertSql(String schema, String side, String directions) {
        return "insert into " + schema + ".examinations (id, visit_id, status, created_at, updated_at, injury_side, treatment_directions) "
                + "values (gen_random_uuid(), gen_random_uuid(), 'DRAFT', now(), now(), " + side + ", " + directions + ")";
    }

    private String columnType(String schema, String column) {
        return jdbc.queryForObject("select data_type from information_schema.columns where table_schema = ? "
                + "and table_name = 'examinations' and column_name = ?", String.class, schema, column);
    }

    private Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        return configuration.load();
    }

    private static String schemaName(String purpose) {
        return "n2_" + purpose + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
