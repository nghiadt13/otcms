package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.repository.PatientMedicalHistoryRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(MedicalHistoryR3PostgresIntegrationTest.TestBindings.class)
class MedicalHistoryR3PostgresIntegrationTest {
    static final String SCHEMA = "kan28_" + UUID.randomUUID().toString().replace("-", "");
    static final Instant NOW = Instant.parse("2026-11-02T03:04:05Z");
    static final List<String> FIELDS = List.of("conditions", "allergies", "procedures", "injuries", "orthopedicHistory");
    @DynamicPropertySource static void schema(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.schema", () -> SCHEMA);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PatientMedicalHistoryRepository histories;
    @Autowired PlatformTransactionManager manager;
    @Autowired PatientProfileAccessService access;
    @Autowired HistoryClock clock;
    final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    UUID patient, own, doctor, ownCareVisit;

    @BeforeEach void setup() {
        clock.now = NOW;
        patient = account(AccountRole.PATIENT); own = profile(patient); doctor = account(AccountRole.DOCTOR);
        ownCareVisit = currentCare(doctor, own);
    }
    UUID account(AccountRole role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,full_name,created_at,updated_at) values (?,?,?,'ACTIVE',?,now(),now())",
                id, "history-" + id + "@example.test", role.name(), "Actor " + id);
        return id;
    }
    UUID doctorProfile(UUID accountId) {
        jdbc.update("insert into doctor_profiles(id,account_id,full_name) values (?,?,?) on conflict (account_id) do nothing",
                UUID.randomUUID(), accountId, "Doctor " + accountId);
        UUID id = jdbc.queryForObject("select id from doctor_profiles where account_id=?", UUID.class, accountId);
        assertNotEquals(accountId, id); // Authorization must resolve account -> Doctor profile.
        return id;
    }
    UUID currentCare(UUID doctorAccountId, UUID target) {
        UUID doctorProfileId = doctorProfile(doctorAccountId), visitId = UUID.randomUUID();
        jdbc.update("insert into visits(id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) "
                + "values (?,?,'WALK_IN',?,?,?,'OPEN')", visitId, target, Timestamp.from(NOW), Timestamp.from(NOW), patient);
        jdbc.update("insert into visit_assignments(id,visit_id,doctor_profile_id,starts_at,ends_at,status,assigned_at) "
                + "values (?,?,?,?,?,'IN_PROGRESS',?)", UUID.randomUUID(), visitId, doctorProfileId,
                Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(1800)), Timestamp.from(NOW));
        return visitId;
    }
    UUID profile(UUID account) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,?,?,?,'2000-01-02','OTHER')",
                id, account, account == null ? "Accountless " + id : "Actor " + account,
                account == null ? null : "+84912345678", account == null ? null : "PATIENT");
        return id;
    }
    UUID link(UUID target, String pending) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_caregivers(id,requester_account_id,dependent_patient_profile_id,state,requested_at,confirmed_at,relationship,verification_method,pending_reason,verified_by_account_id,verified_at) "
                + "values (?,?,?, ?,?,?, 'CHILD',?,?,?,?)", id, patient, target, pending == null ? "CONFIRMED" : "REQUESTED",
                Timestamp.from(NOW), pending == null ? Timestamp.from(NOW) : null,
                pending == null ? "CREATED_WITHOUT_OTP" : null, pending, pending == null ? patient : null,
                pending == null ? Timestamp.from(NOW) : null);
        return id;
    }
    RequestPostProcessor actor(UUID id, AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(id, role, UUID.randomUUID()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
    String path(UUID profile) { return "/api/v1/patient-profiles/" + profile + "/medical-history"; }
    Map<String,Object> body(Object version) {
        Map<String,Object> values = new LinkedHashMap<>(); FIELDS.forEach(f -> values.put(f, ""));
        values.put("version", version); return values;
    }
    ResultActions fetch(UUID caller, AccountRole role, UUID target) throws Exception {
        return mvc.perform(get(path(target)).with(actor(caller, role)));
    }
    ResultActions write(UUID caller, AccountRole role, UUID target, Map<String,Object> body) throws Exception {
        return mvc.perform(put(path(target)).with(actor(caller, role)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(body)));
    }
    JsonNode response(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString());
    }
    int count(UUID target) { return jdbc.queryForObject("select count(*) from patient_medical_histories where patient_profile_id=?", Integer.class, target); }
    void empty(ResultActions action) throws Exception {
        var node = response(action.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")));
        assertEquals(8, node.size()); FIELDS.forEach(field -> assertEquals("", node.get(field).asText()));
        assertTrue(node.has("updatedAt") && node.get("updatedAt").isNull());
        assertTrue(node.has("updatedBy") && node.get("updatedBy").isNull()); assertEquals(0, node.get("version").asLong());
    }

    @ParameterizedTest @ValueSource(strings = {"SELF", "DEPENDENT", "DOCTOR"})
    void getWithoutRowReturnsExactEmptyContractWithoutInsert(String scope) throws Exception {
        UUID target = own, caller = patient; AccountRole role = AccountRole.PATIENT;
        if (scope.equals("DEPENDENT")) { target = profile(null); link(target, null); assertTrue(access.canAccess(patient, target)); }
        if (scope.equals("DOCTOR")) { caller = doctor; role = AccountRole.DOCTOR; }
        empty(fetch(caller, role, target)); assertEquals(0, count(target));
        assertEquals(1, jdbc.queryForObject("select count(*) from doctor_profiles where account_id=?", Integer.class, doctor));
    }
    @Test void firstPutTrimsPersistsActorAndClockThenGetReturnsExactFields() throws Exception {
        var body = body(0); FIELDS.forEach(field -> body.put(field, "  Đau  khớp\nDòng hai e\u0301  "));
        body.put("updatedBy", "not-the-actor@example.test"); body.put("updatedAt", "1900-01-01");
        var saved = response(write(patient, AccountRole.PATIENT, own, body).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")));
        FIELDS.forEach(field -> assertEquals("Đau  khớp\nDòng hai e\u0301", saved.get(field).asText()));
        assertEquals(1, saved.get("version").asLong()); assertEquals("Actor " + patient, saved.get("updatedBy").asText());
        assertEquals("2026-11-02T10:04:05+07:00", saved.get("updatedAt").asText()); assertEquals(8, saved.size());
        assertEquals(patient, jdbc.queryForObject("select updated_by_account_id from patient_medical_histories where patient_profile_id=?", UUID.class, own));
        assertEquals(Timestamp.from(NOW), jdbc.queryForObject("select updated_at from patient_medical_histories where patient_profile_id=?", Timestamp.class, own));
        var fetched = response(fetch(doctor, AccountRole.DOCTOR, own).andExpect(status().isOk())); assertEquals(saved, fetched);
        assertFalse(fetched.toString().contains("@example.test")); assertFalse(fetched.has("patientProfileId"));
    }
    @Test void updateChangesActorAndUnchangedTextStillIncrements() throws Exception {
        write(patient, AccountRole.PATIENT, own, body(0)).andExpect(status().isOk());
        clock.now = NOW.plusSeconds(60);
        var change = body(1); change.put("allergies", "Penicillin");
        write(doctor, AccountRole.DOCTOR, own, change).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.updatedBy").value("Actor " + doctor))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-02T10:05:05+07:00"));
        change.put("version", 2);
        clock.now = NOW.plusSeconds(90);
        write(patient, AccountRole.PATIENT, own, change).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-02T10:05:35+07:00"));
        jdbc.update("update patient_profiles set full_name='Renamed editor' where id=?", own);
        fetch(doctor, AccountRole.DOCTOR, own).andExpect(status().isOk()).andExpect(jsonPath("$.updatedBy").value("Renamed editor"));
    }
    @ParameterizedTest @ValueSource(strings = {"conditions", "allergies", "procedures", "injuries", "orthopedicHistory"})
    void eachFieldAccepts2000AfterTrimAndRejects2001(String field) throws Exception {
        var value = body(0); value.put(field, "  " + "x".repeat(2000) + " \n");
        write(patient, AccountRole.PATIENT, own, value).andExpect(status().isOk());
        value.put("version", 1); value.put(field, "x".repeat(2001));
        write(patient, AccountRole.PATIENT, own, value).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("Thông tin tiền sử bệnh chưa hợp lệ. Vui lòng kiểm tra và chỉnh sửa."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field)).andExpect(jsonPath("$.fieldErrors[0].code").value("MSG-20"));
        fetch(patient, AccountRole.PATIENT, own).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }
    @ParameterizedTest @ValueSource(strings = {"conditions", "allergies", "procedures", "injuries", "orthopedicHistory"})
    void everyTextRequiresStringAndPresence(String field) throws Exception {
        for (Object invalid : Arrays.asList(null, 42, true, List.of("text"), Map.of("value", "text"))) {
            var value = body(0); value.put(field, invalid);
            write(patient, AccountRole.PATIENT, own, value).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
        var absent = body(0); absent.remove(field);
        write(patient, AccountRole.PATIENT, own, absent).andExpect(status().isBadRequest()); assertEquals(0, count(own));
    }
    @Test void supplementaryCharactersCountUtf16AndWhitespaceOnlyBecomesEmpty() throws Exception {
        var value = body(0); value.put("conditions", "😀".repeat(1000));
        write(patient, AccountRole.PATIENT, own, value).andExpect(status().isOk());
        value.put("version", 1); value.put("conditions", "😀".repeat(1001));
        write(patient, AccountRole.PATIENT, own, value).andExpect(status().isBadRequest());
        FIELDS.forEach(field -> value.put(field, " \n\t\r  "));
        write(patient, AccountRole.PATIENT, own, value).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        var result = response(fetch(patient, AccountRole.PATIENT, own)); FIELDS.forEach(field -> assertEquals("", result.get(field).asText()));
    }
    static Stream<Arguments> invalidVersions() {
        return Stream.of(null, "0", "1.0", 0.0, -1, true, List.of(0), Map.of("value", 0), new java.math.BigInteger("9223372036854775808"))
                .map(Arguments::of);
    }
    @ParameterizedTest @MethodSource("invalidVersions")
    void versionCannotBeCoerced(Object version) throws Exception {
        write(patient, AccountRole.PATIENT, own, body(version)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED")); assertEquals(0, count(own));
    }
    @Test void missingVersionAndMalformedBodyAreValidationErrors() throws Exception {
        var value = body(0); value.remove("version");
        write(patient, AccountRole.PATIENT, own, value).andExpect(status().isBadRequest());
        mvc.perform(put(path(own)).with(actor(patient, AccountRole.PATIENT)).with(csrf()).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
    @ParameterizedTest @EnumSource(AccountRole.class)
    void roleMatrixForGetAndPut(AccountRole role) throws Exception {
        UUID caller = role == AccountRole.PATIENT ? patient : account(role);
        if (role == AccountRole.DOCTOR) currentCare(caller, own);
        int expected = role == AccountRole.PATIENT || role == AccountRole.DOCTOR ? 200 : 403;
        var first = fetch(caller, role, own).andExpect(status().is(expected));
        var second = write(caller, role, own, body(0)).andExpect(status().is(expected));
        if (expected == 403) {
            first.andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
            second.andExpect(jsonPath("$.error.code").value("ACCESS_DENIED")); assertEquals(0, count(own));
        }
    }
    static Stream<Arguments> inactiveActors() {
        return Stream.of(AccountRole.PATIENT, AccountRole.DOCTOR).flatMap(role ->
                Stream.of("PENDING_VERIFICATION", "TEMPORARILY_LOCKED", "DISABLED", "ARCHIVED").map(status -> Arguments.of(role, status)));
    }
    @ParameterizedTest @MethodSource("inactiveActors")
    void inactiveActorsCannotUseValidLookingPrincipal(AccountRole role, String status) throws Exception {
        UUID caller = role == AccountRole.PATIENT ? patient : doctor;
        jdbc.update("update accounts set status=? where id=?", status, caller);
        fetch(caller, role, own).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        write(caller, role, own, body(0)).andExpect(status().isForbidden());
    }
    @Test void claimedRoleMustMatchActualAccount() throws Exception {
        fetch(patient, AccountRole.DOCTOR, own).andExpect(status().isForbidden());
        write(patient, AccountRole.DOCTOR, own, body(0)).andExpect(status().isForbidden());
        fetch(doctor, AccountRole.PATIENT, own).andExpect(status().isForbidden()); assertEquals(0, count(own));
    }
    @Test void missingOwnProfileFailsClosedEvenForConfirmedDependent() throws Exception {
        UUID target = profile(null); link(target, null);
        // Remove only this test's care fixture before removing its referenced Patient profile.
        jdbc.update("delete from visit_assignments where visit_id=?", ownCareVisit);
        jdbc.update("delete from visits where id=?", ownCareVisit);
        jdbc.update("delete from patient_profiles where id=?", own);
        assertFalse(access.canAccess(patient, target));
        fetch(patient, AccountRole.PATIENT, target).andExpect(status().isNotFound());
        write(patient, AccountRole.PATIENT, target, body(0)).andExpect(status().isNotFound()); assertEquals(0, count(target));
    }
    @ParameterizedTest @ValueSource(strings = {"OTP", "COUNTER", "REVOKED", "UNRELATED", "MISSING"})
    void inaccessibleAndMissingTargetsHaveSamePatientError(String state) throws Exception {
        UUID target = state.equals("MISSING") ? UUID.randomUUID() : profile(null);
        if (List.of("OTP", "COUNTER").contains(state)) link(target, state);
        if (state.equals("REVOKED")) {
            link(target, null);
            mvc.perform(delete("/api/v1/me/dependents/{id}", target).with(actor(patient, AccountRole.PATIENT)).with(csrf())).andExpect(status().isNoContent());
        }
        if (!state.equals("MISSING")) {
            var seed = body(0); seed.put("allergies", "Private clinical text");
            currentCare(doctor, target);
            write(doctor, AccountRole.DOCTOR, target, seed).andExpect(status().isOk());
        }
        fetch(patient, AccountRole.PATIENT, target).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PATIENT_PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Không thể truy cập hồ sơ bệnh nhân này. Vui lòng kiểm tra lại thông tin hoặc quyền truy cập."));
        write(patient, AccountRole.PATIENT, target, body(0)).andExpect(status().isNotFound());
        if (!state.equals("MISSING")) assertEquals(1L, jdbc.queryForObject("select version from patient_medical_histories where patient_profile_id=?", Long.class, target));
    }
    @Test void confirmedDependentPutUsesRequesterAsEditorAndMissingTargetRespectsActorScope() throws Exception {
        UUID target = profile(null); link(target, null);
        write(patient, AccountRole.PATIENT, target, body(0)).andExpect(status().isOk()).andExpect(jsonPath("$.updatedBy").value("Actor " + patient));
        assertEquals(patient, jdbc.queryForObject("select updated_by_account_id from patient_medical_histories where patient_profile_id=?", UUID.class, target));
        UUID missing = UUID.randomUUID();
        // Current-care authorization precedes existence lookup for Doctors (retired R3 shortcut).
        fetch(doctor, AccountRole.DOCTOR, missing).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        write(doctor, AccountRole.DOCTOR, missing, body(0)).andExpect(status().isForbidden());
        fetch(patient, AccountRole.PATIENT, missing).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PATIENT_PROFILE_NOT_FOUND"));
        write(patient, AccountRole.PATIENT, missing, body(0)).andExpect(status().isNotFound());
    }
    @Test void activeDoctorWithoutProfileOrCurrentCareCannotReadOrWrite() throws Exception {
        UUID unrelatedDoctor = account(AccountRole.DOCTOR);
        fetch(unrelatedDoctor, AccountRole.DOCTOR, own).andExpect(status().isForbidden());
        write(unrelatedDoctor, AccountRole.DOCTOR, own, body(0)).andExpect(status().isForbidden());
        doctorProfile(unrelatedDoctor);
        fetch(unrelatedDoctor, AccountRole.DOCTOR, own).andExpect(status().isForbidden());
        write(unrelatedDoctor, AccountRole.DOCTOR, own, body(0)).andExpect(status().isForbidden());
        UUID unrelatedPatient = profile(null);
        fetch(doctor, AccountRole.DOCTOR, unrelatedPatient).andExpect(status().isForbidden());
        write(doctor, AccountRole.DOCTOR, unrelatedPatient, body(0)).andExpect(status().isForbidden());
        assertEquals(0, count(own)); assertEquals(0, count(unrelatedPatient));
    }
    @Test void anonymousAndCsrfAreNotWeakened() throws Exception {
        mvc.perform(get(path(own))).andExpect(status().isUnauthorized());
        mvc.perform(put(path(own)).with(csrf()).contentType("application/json").content(json.writeValueAsString(body(0)))).andExpect(status().isUnauthorized());
        mvc.perform(put(path(own)).with(actor(patient, AccountRole.PATIENT)).contentType("application/json").content(json.writeValueAsString(body(0)))).andExpect(status().isForbidden());
        assertEquals(0, count(own));
    }
    @Test void staleAndPositiveVersionWithoutRowNeverWrite() throws Exception {
        write(patient, AccountRole.PATIENT, own, body(1)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
        assertEquals(0, count(own)); write(patient, AccountRole.PATIENT, own, body(0)).andExpect(status().isOk());
        for (int version : List.of(0, 2)) write(doctor, AccountRole.DOCTOR, own, body(version)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại."));
        assertEquals(1L, jdbc.queryForObject("select version from patient_medical_histories where patient_profile_id=?", Long.class, own));
    }
    List<org.springframework.mock.web.MockHttpServletResponse> race(Callable<org.springframework.mock.web.MockHttpServletResponse> a,
            Callable<org.springframework.mock.web.MockHttpServletResponse> b) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return a.call(); });
            var second = pool.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return b.call(); });
            start.countDown(); return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void actualHttpCreateAndUpdateRacesHaveExactlyOneWinner(boolean firstCreate) throws Exception {
        if (!firstCreate) write(patient, AccountRole.PATIENT, own, body(0)).andExpect(status().isOk());
        int version = firstCreate ? 0 : 1;
        fetch(patient, AccountRole.PATIENT, own).andExpect(jsonPath("$.version").value(version));
        fetch(doctor, AccountRole.DOCTOR, own).andExpect(jsonPath("$.version").value(version));
        var a = body(version); a.put("conditions", "Patient wins");
        var b = body(version); b.put("conditions", "Doctor wins");
        var replies = race(() -> write(patient, AccountRole.PATIENT, own, a).andReturn().getResponse(),
                () -> write(doctor, AccountRole.DOCTOR, own, b).andReturn().getResponse());
        assertEquals(List.of(200, 409), replies.stream().map(r -> r.getStatus()).sorted().toList());
        var loser = replies.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
        assertEquals("STALE_VERSION", json.readTree(loser.getContentAsString()).get("error").get("code").asText());
        boolean patientWon = replies.getFirst().getStatus() == 200;
        assertEquals(patientWon ? "Patient wins" : "Doctor wins", jdbc.queryForObject("select conditions from patient_medical_histories where patient_profile_id=?", String.class, own));
        assertEquals(patientWon ? patient : doctor, jdbc.queryForObject("select updated_by_account_id from patient_medical_histories where patient_profile_id=?", UUID.class, own));
        assertEquals(1, count(own)); assertEquals(version + 1L, jdbc.queryForObject("select version from patient_medical_histories where patient_profile_id=?", Long.class, own));
    }
    @Test void repositoryInsertRaceUsesIndependentRealTransactionsWithoutGlobalGuard() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> insert = () -> new TransactionTemplate(manager).execute(status -> {
                try { assertTrue(start.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted test"); }
                return histories.insertInitial(own, "", "", "", "", "", NOW, patient);
            });
            var a = pool.submit(insert); var b = pool.submit(insert); start.countDown();
            assertEquals(List.of(0, 1), Stream.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)).sorted().toList());
            assertEquals(1, count(own));
        }
    }
    @Test void revokeRaceAndRequestAfterCommittedRevokeCannotWrite() throws Exception {
        UUID target = profile(null); link(target, null);
        var replies = race(() -> write(patient, AccountRole.PATIENT, target, body(0)).andReturn().getResponse(),
                () -> mvc.perform(delete("/api/v1/me/dependents/{id}", target).with(actor(patient, AccountRole.PATIENT)).with(csrf())).andReturn().getResponse());
        assertTrue(List.of(200, 404).contains(replies.getFirst().getStatus())); assertEquals(204, replies.getLast().getStatus());
        int before = count(target); assertEquals(replies.getFirst().getStatus() == 200 ? 1 : 0, before);
        assertFalse(access.canAccess(patient, target));
        write(patient, AccountRole.PATIENT, target, body(before)).andExpect(status().isNotFound()); assertEquals(before, count(target));
        assertEquals(1, jdbc.queryForObject("select count(*) from patient_profiles where id=?", Integer.class, target));
    }
    @Test void schemaEnforcesUniquenessReferencesDefaultsLengthsAndVersion() {
        jdbc.update("insert into patient_medical_histories(patient_profile_id,updated_at,updated_by_account_id) values (?,?,?)", own, Timestamp.from(NOW), doctor);
        assertEquals(1L, jdbc.queryForObject("select version from patient_medical_histories where patient_profile_id=?", Long.class, own));
        assertEquals("", jdbc.queryForObject("select conditions from patient_medical_histories where patient_profile_id=?", String.class, own));
        assertThrows(Exception.class, () -> jdbc.update("insert into patient_medical_histories(patient_profile_id,updated_at,updated_by_account_id) values (?,?,?)", own, Timestamp.from(NOW), patient));
        assertThrows(Exception.class, () -> jdbc.update("insert into patient_medical_histories(patient_profile_id,updated_at,updated_by_account_id) values (?,?,?)", UUID.randomUUID(), Timestamp.from(NOW), patient));
        UUID another = profile(null);
        assertThrows(Exception.class, () -> jdbc.update("insert into patient_medical_histories(patient_profile_id,updated_at,updated_by_account_id) values (?,?,?)", another, Timestamp.from(NOW), UUID.randomUUID()));
        for (String column : List.of("conditions", "allergies", "procedures", "injuries", "orthopedic_history")) {
            assertThrows(Exception.class, () -> jdbc.update("update patient_medical_histories set " + column + "=null where patient_profile_id=?", own));
            jdbc.update("update patient_medical_histories set " + column + "=? where patient_profile_id=?", "x".repeat(2000), own);
            assertThrows(Exception.class, () -> jdbc.update("update patient_medical_histories set " + column + "=? where patient_profile_id=?", "x".repeat(2001), own));
        }
        assertThrows(Exception.class, () -> jdbc.update("update patient_medical_histories set version=0 where patient_profile_id=?", own));
        assertThrows(Exception.class, () -> jdbc.update("update patient_medical_histories set updated_at=null where patient_profile_id=?", own));
        assertThrows(Exception.class, () -> jdbc.update("update patient_medical_histories set updated_by_account_id=null where patient_profile_id=?", own));
        assertThrows(Exception.class, () -> jdbc.update("delete from patient_profiles where id=?", own));
        assertThrows(Exception.class, () -> jdbc.update("delete from accounts where id=?", doctor));
        assertEquals(9, jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=? and table_name='patient_medical_histories'", Integer.class, SCHEMA));
    }
    @Test void migrationAppliesAfterV072OutOfOrderAndPreservesV049() throws Exception {
        String upgrade = "kan28_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"), "kan28-before-");
        try (var files = java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/db/migration"))) {
            for (var source : files.filter(p -> !p.getFileName().toString().startsWith("V051__")).toList())
                java.nio.file.Files.copy(source, directory.resolve(source.getFileName()));
        }
        Flyway.configure().dataSource(dataSource).schemas(upgrade).defaultSchema(upgrade).locations("filesystem:" + directory.toAbsolutePath()).load().migrate();
        Integer checksum = jdbc.queryForObject("select checksum from " + upgrade + ".flyway_schema_history where version::numeric=49", Integer.class);
        UUID legacy = UUID.randomUUID();
        jdbc.update("insert into " + upgrade + ".patient_profiles(id,full_name,date_of_birth,gender) values (?,'Legacy accountless','2000-01-02','OTHER')", legacy);
        var result = Flyway.configure().dataSource(dataSource).schemas(upgrade).defaultSchema(upgrade).outOfOrder(true).load().migrate();
        assertEquals(1, result.migrationsExecuted);
        assertEquals(checksum, jdbc.queryForObject("select checksum from " + upgrade + ".flyway_schema_history where version::numeric=49", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + upgrade + ".patient_profiles where id=? and phone_canonical is null", Integer.class, legacy));
        assertEquals(0, jdbc.queryForObject("select count(*) from " + upgrade + ".patient_medical_histories", Integer.class));
    }
    @Test void openApiContainsR3RouteAndBothMethods() throws Exception {
        var document = response(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        var route = document.get("paths").get("/api/v1/patient-profiles/{id}/medical-history");
        assertNotNull(route); assertNotNull(route.get("get")); assertNotNull(route.get("put").get("responses").get("409"));
        assertNotNull(document.get("paths").get("/api/v1/me/dependents"));
    }
    static class HistoryClock extends Clock {
        volatile Instant now = NOW;
        public Instant instant() { return now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TestBindings {
        @Bean @Primary HistoryClock medicalHistoryClock() { return new HistoryClock(); }
    }
}
