package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientProfileException;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @LocalPostgresTest @Transactional @AutoConfigureMockMvc
class PatientProfileT007PostgresIntegrationTest {
    static final String SCHEMA = "kan24_it_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void schema(DynamicPropertyRegistry registry) {
        String base = System.getProperty("spring.datasource.url", System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:" + System.getenv().getOrDefault("OTCMS_TEST_DB_PORT", "5432") + "/" + System.getenv().getOrDefault("OTCMS_TEST_DB_NAME", "otcms_test_db")));
        String url = base.contains("currentSchema=") ? base.replaceAll("currentSchema=[^&]*", "currentSchema=" + SCHEMA + ",public")
                : base + (base.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }
    @Autowired PatientProfileService service;
    @Autowired PatientProfileDirectory directory;
    @Autowired PatientProfileRepository repository;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    final UUID actor = UUID.randomUUID();
    final LocalDate dob = LocalDate.of(2000, 1, 2);
    @BeforeEach void receptionist() { account(actor, "RECEPTIONIST"); }
    void account(UUID id, String type) {
        jdbc.update("insert into accounts(id, normalized_email, account_type, status, created_at, updated_at) "
                + "values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", type);
    }
    void profile(UUID id, UUID account, String name, String phone) {
        jdbc.update("insert into patient_profiles(id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) "
                + "values (?, ?, ?, ?, 'PATIENT', ?, 'OTHER')", id, account, name, phone, dob);
    }
    @Test void createPersistsAdministrativeFieldsWithoutCreatingAnAccount() {
        int before = jdbc.queryForObject("select count(*) from accounts", Integer.class);
        var saved = service.create(actor, "Nguyễn An", "0912345678", "GUARDIAN", dob, "OTHER",
                " Address ", "12345", "PERSONAL_ID");
        em.flush(); em.clear();
        var entity = repository.findById(saved.id()).orElseThrow();
        assertNull(entity.getAccountId()); assertNull(entity.getAccount());
        assertEquals("Address", entity.getAddress()); assertEquals("12345", entity.getNationalId());
        assertEquals("PERSONAL_ID", entity.getIdentityDocumentType().name());
        assertFalse(service.detail(actor, saved.id()).hasAccount());
        assertEquals(before, jdbc.queryForObject("select count(*) from accounts", Integer.class));
    }
    @Test void exactMatchingAndDuplicateUseDatabaseNormalizationWithoutRemovingAccents() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        String decomposed = java.text.Normalizer.normalize("  Nguyễn\t  An ", java.text.Normalizer.Form.NFD);
        profile(first, null, decomposed, "+84912345678");
        profile(second, null, "Other Patient", "+84912345678");
        assertTrue(directory.duplicateExists("+84912345678", dob, "NGUYỄN AN"));
        assertFalse(directory.duplicateExists("+84912345678", dob, "Nguyen An"));
        assertFalse(directory.duplicateExists("+84912345678", dob, "Nguyễn"));
        assertTrue(directory.match("+8491234567", dob, null).isEmpty());
        assertTrue(directory.match("+84912345678", dob.plusDays(1), null).isEmpty());
        assertEquals(first, service.matches(actor, "0912 345 678", dob, " nguyễn   an ").getFirst().id());
        assertTrue(service.matches(actor, "0912345678", dob, "Missing Patient").isEmpty());
        assertEquals("PATIENT_MATCH_AMBIGUOUS", assertThrows(PatientProfileException.class,
                () -> service.matches(actor, "0912345678", dob, null)).getMessage());
        profile(UUID.randomUUID(), null, "NGUYỄN AN", "+84912345678");
        assertEquals("PATIENT_MATCH_AMBIGUOUS", assertThrows(PatientProfileException.class,
                () -> service.matches(actor, "0912345678", dob, "Nguyễn An")).getMessage());
        assertEquals("PATIENT_PROFILE_DUPLICATE", assertThrows(PatientProfileException.class,
                () -> service.create(actor, " Nguyễn  An ", "0912345678", "PATIENT", dob, "OTHER", "Address", null, null)).getMessage());
    }
    @Test void managedUpdateIncrementsVersionKeepsAccountAndAllowsNullAddress() {
        UUID owner = UUID.randomUUID(), id = UUID.randomUUID(); account(owner, "PATIENT");
        profile(id, owner, "Original", "+84912345678");
        var initial = service.detail(actor, id);
        var updated = service.update(actor, id, "Updated", "0912345678", "GUARDIAN", dob, "FEMALE",
                null, null, null, initial.version());
        assertEquals(initial.version() + 1, updated.version()); assertTrue(updated.hasAccount()); assertNull(updated.address());
        em.clear(); assertEquals(owner, repository.findById(id).orElseThrow().getAccountId());
        assertEquals("STALE_VERSION", assertThrows(PatientProfileException.class, () -> service.update(actor, id,
                "Stale", "0912345678", "PATIENT", dob, "OTHER", null, null, null, initial.version())).getMessage());
    }
    @Test void nullNationalIdAndSameNationalIdOnDifferentProfilesAreAllowed() {
        service.create(actor, "First Patient", "0912345678", "PATIENT", dob, "OTHER", "Address", "SHARED", "PASSPORT");
        service.create(actor, "Second Patient", "0912345678", "PATIENT", dob, "OTHER", "Address", "SHARED", "PASSPORT");
        var guest = service.create(actor, "Third Patient", "0912345678", "PATIENT", dob, "OTHER", "Address", null, null);
        assertNull(service.detail(actor, guest.id()).nationalId());
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentManagedTransactionsAllowOnlyOneWinner() throws Exception {
        UUID id = UUID.randomUUID(); profile(id, null, "Original", "+84912345678");
        var loaded = new CountDownLatch(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> results = new ArrayList<>();
            for (String name : List.of("First writer", "Second writer")) results.add(pool.submit(() -> {
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(status -> {
                        var entity = repository.findById(id).orElseThrow();
                        loaded.countDown();
                        try { if (!loaded.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent load timeout"); }
                        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                        entity.setFullName(name);
                        repository.flush();
                    });
                    return null;
                } catch (Throwable failure) { return failure; }
            }));
            var failures = new ArrayList<Throwable>();
            for (var result : results) { var failure = result.get(20, TimeUnit.SECONDS); if (failure != null) failures.add(failure); }
            assertEquals(1, failures.size());
            assertInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class, failures.getFirst());
            assertEquals(1L, jdbc.queryForObject("select version from patient_profiles where id = ?", Long.class, id));
        } finally { pool.shutdownNow(); }
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void competingCreatesUseTheExistingCoordinationGuardToPreventDuplicates() throws Exception {
        String name = "Concurrent " + UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Create start timeout");
                try {
                    service.create(actor, name, "0912345678", "PATIENT", dob, "OTHER", "Address", null, null);
                    return "CREATED";
                } catch (PatientProfileException rejected) { return rejected.getMessage(); }
            }));
            start.countDown();
            var outcomes = new ArrayList<String>();
            for (var result : results) outcomes.add(result.get(20, TimeUnit.SECONDS));
            assertEquals(Set.of("CREATED", "PATIENT_PROFILE_DUPLICATE"), new HashSet<>(outcomes));
            assertEquals(1, jdbc.queryForObject("select count(*) from patient_profiles where full_name=?", Integer.class, name));
        } finally { pool.shutdownNow(); }
    }
    @Test void openApiDocumentsNewAndExistingPatientRoutesWithoutRemovingOtherFeatures() throws Exception {
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response);
        var paths = document.get("paths");
        String root = "/api/v1/patient-profiles";
        assertNotNull(paths.get(root).get("get"));
        assertNotNull(paths.get(root).get("post").get("responses").get("201"));
        assertNull(paths.get(root).get("post").get("responses").get("200"));
        assertNotNull(paths.get(root + "/matches").get("get").get("responses").get("409"));
        assertNotNull(paths.get(root + "/{id}").get("get"));
        assertNotNull(paths.get(root + "/{id}").get("put").get("responses").get("409"));
        assertNotNull(paths.get(root + "/{patientProfileId}/reception-verification"));
        assertNotNull(paths.get("/api/v1/public/reviews"));
        assertNotNull(paths.get("/api/v1/appointments"));
    }
    Flyway migration(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
    }
    String legacySchema(String identifier) {
        String schema = "kan24_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        migration(schema, "47").migrate();
        jdbc.update("insert into " + schema + ".patient_profiles(id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender,address,citizen_id) "
                + "values (?, 'Legacy', '+84912345678', 'PATIENT', '2000-01-02', 'OTHER', ?, ?)", UUID.randomUUID(), "Legacy address", identifier);
        return schema;
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void migrationFromV047PreservesLegacyDataAndCreatesExpectedSchema() {
        String schema = legacySchema("12345678901234567890");
        assertEquals(1, migration(schema, "48").migrate().migrationsExecuted);
        var row = jdbc.queryForMap("select address,national_id,identity_document_type,version from " + schema + ".patient_profiles");
        assertEquals("Legacy address", row.get("address")); assertEquals("12345678901234567890", row.get("national_id"));
        assertNull(row.get("identity_document_type")); assertEquals(0L, row.get("version"));
        assertEquals("text", jdbc.queryForObject("select data_type from information_schema.columns where table_schema=? "
                + "and table_name='patient_profiles' and column_name='address'", String.class, schema));
        assertEquals(20, jdbc.queryForObject("select character_maximum_length from information_schema.columns where table_schema=? "
                + "and table_name='patient_profiles' and column_name='national_id'", Integer.class, schema));
        assertEquals(1, jdbc.queryForObject("select count(*) from pg_indexes where schemaname=? and indexname='ix_patient_profiles_national_id' "
                + "and indexdef not like '%UNIQUE%'", Integer.class, schema));
        for (String type : List.of("CCCD", "PERSONAL_ID", "PASSPORT"))
            assertEquals(1, jdbc.update("update " + schema + ".patient_profiles set identity_document_type=?", type));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update " + schema + ".patient_profiles set identity_document_type='INVALID'"));
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void oversizedLegacyIdentifierFailsSafelyWithoutTruncationOrPartialDDL() {
        String identifier = "123456789012345678901";
        String schema = legacySchema(identifier);
        var failure = assertThrows(org.flywaydb.core.api.FlywayException.class, () -> migration(schema, "48").migrate());
        assertTrue(failure.getMessage().contains("legacy values exceed 20 characters"));
        assertEquals(identifier, jdbc.queryForObject("select citizen_id from " + schema + ".patient_profiles", String.class));
        assertEquals("Legacy address", jdbc.queryForObject("select address from " + schema + ".patient_profiles", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=? "
                + "and table_name='patient_profiles' and column_name='national_id'", Integer.class, schema));
    }
}
