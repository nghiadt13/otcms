package fu.fa26.sep490.g58.otcms_be.patient;

import fu.fa26.sep490.g58.otcms_be.auth.service.EmailDeliveryService;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.Relationship;
import fu.fa26.sep490.g58.otcms_be.patient.service.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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
import org.springframework.test.web.servlet.request.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(DependentsR3PostgresIntegrationTest.TestBindings.class)
class DependentsR3PostgresIntegrationTest {
    static final String SCHEMA = "kan26_" + UUID.randomUUID().toString().replace("-", "");
    static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    @DynamicPropertySource static void schema(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.schema", () -> SCHEMA);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PatientProfileAccessService access;
    @Autowired AuthorizePatientProfileService authorization;
    @Autowired DependentProfileService service;
    @Autowired RecordingEmail delivery;
    @Autowired TestClock clock;
    final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    UUID actor, own, receptionist;
    String name, number;

    @BeforeEach void setup() {
        clock.now = NOW; delivery.fail = false; delivery.gate.set(null);
        actor = account("PATIENT"); own = profile(actor, "Self " + actor, actor.toString().substring(0, 20), "CCCD");
        receptionist = account("RECEPTIONIST");
        name = "Nguyễn An " + UUID.randomUUID(); number = UUID.randomUUID().toString().substring(0, 20);
    }
    UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts(id, normalized_email, account_type, status, created_at, updated_at) values (?,?,?,'ACTIVE',now(),now())",
                id, id + "@example.test", role);
        return id;
    }
    UUID profile(UUID account, String fullName, String document, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender,national_id,identity_document_type) "
                + "values (?,?,?,'+84912345678','PATIENT','2000-01-02','OTHER',?,?)", id, account, fullName, document, type);
        return id;
    }
    RequestPostProcessor actor(UUID account, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(account,
                AccountRole.valueOf(role), UUID.randomUUID()), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    String body(String fullName, String document, String type) {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("fullName", fullName); values.put("dateOfBirth", "2000-01-02"); values.put("gender", "OTHER");
        values.put("nationalId", document); values.put("identityDocumentType", type); values.put("relationship", "CHILD");
        return json.writeValueAsString(values);
    }
    JsonNode add(String document, String type, int status) throws Exception {
        var response = mvc.perform(post("/api/v1/me/dependents").with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(body(name, document, type))).andExpect(status().is(status)).andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }
    UUID id(JsonNode result, String field) { return UUID.fromString(result.get(field).asText()); }
    ResultActions verify(UUID owner, UUID link, String code) throws Exception {
        return mvc.perform(post("/api/v1/me/dependents/links/{id}/otp/verify", link).with(actor(owner, "PATIENT")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("code", code))));
    }
    ResultActions resend(UUID owner, UUID link) throws Exception {
        return mvc.perform(post("/api/v1/me/dependents/links/{id}/otp/resend", link).with(actor(owner, "PATIENT")).with(csrf()));
    }
    UUID counterLink() throws Exception { profile(null, name, number, "CCCD"); return id(add(number, "CCCD", 202), "linkId"); }
    UUID otpLink(UUID recipient) throws Exception { profile(recipient, name, number, "CCCD"); return id(add(number, "CCCD", 202), "linkId"); }

    @Test void branchACreatesNullContactAndImmediateAccessAtomically() throws Exception {
        int accountsBefore = jdbc.queryForObject("select count(*) from accounts", Integer.class);
        var result = add(number, "CCCD", 201);
        UUID target = id(result.get("profile"), "patientProfileId"), link = id(result, "linkId");
        var row = jdbc.queryForMap("select * from patient_profiles where id=?", target);
        assertNull(row.get("account_id")); assertNull(row.get("phone_canonical"));
        assertNull(row.get("phone_owner_type")); assertNull(row.get("address"));
        assertEquals(accountsBefore, jdbc.queryForObject("select count(*) from accounts", Integer.class));
        assertTrue(access.canAccess(actor, target)); assertEquals(AuthorizePatientProfileService.AuthorizationScope.DEPENDENT,
                authorization.authorize(actor, target).scope());
        assertEquals("CREATED_WITHOUT_OTP", jdbc.queryForObject("select verification_method from patient_caregivers where id=?", String.class, link));
        assertEquals(0, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=?", Integer.class, link));
    }
    @Test void linkFailureRollsBackNewProfile() throws Exception {
        jdbc.execute("create function kan26_reject_link() returns trigger language plpgsql as $$ begin raise exception 'test link failure'; end; $$");
        jdbc.execute("create trigger kan26_reject_link before insert on patient_caregivers for each row execute function kan26_reject_link()");
        try { add(number, "CCCD", 500); }
        finally { jdbc.execute("drop trigger kan26_reject_link on patient_caregivers"); jdbc.execute("drop function kan26_reject_link()"); }
        assertEquals(0, jdbc.queryForObject("select count(*) from patient_profiles where full_name=?", Integer.class, name));
    }
    @Test void branchBRecipientMaskHashAndVerify() throws Exception {
        UUID recipient = account("PATIENT"), target = profile(recipient, name, number, "CCCD");
        var result = add(number, "CCCD", 202); UUID link = id(result, "linkId");
        assertEquals("OTP_SENT", result.get("result").asText()); assertEquals("***@example.test", result.get("maskedEmail").asText());
        assertEquals(recipient + "@example.test", delivery.addresses.get(recipient)); assertFalse(delivery.codes.containsKey(actor));
        assertFalse(access.canAccess(actor, target));
        String code = delivery.codes.get(recipient);
        assertNotEquals(code, jdbc.queryForObject("select code_hash from verification_codes where purpose='CARE_LINK' and link_id=?", String.class, link));
        verify(actor, link, code).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("LINKED"));
        assertTrue(access.canAccess(actor, target));
        var row = jdbc.queryForMap("select * from patient_caregivers where id=?", link);
        assertEquals(actor, row.get("verified_by_account_id")); assertEquals(row.get("confirmed_at"), row.get("verified_at"));
        verify(actor, link, code).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CARE_LINK_NOT_AVAILABLE"));
    }
    @Test void failedAttemptsCommitAndFifthFailureExhausts() throws Exception {
        UUID recipient = account("PATIENT"), link = otpLink(recipient);
        String wrong = "000000".equals(delivery.codes.get(recipient)) ? "111111" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            verify(actor, link, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
            assertEquals(attempt, jdbc.queryForObject("select failed_attempts from verification_codes where purpose='CARE_LINK' and link_id=?", Integer.class, link));
        }
        verify(actor, link, delivery.codes.get(recipient)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
        assertNotNull(jdbc.queryForObject("select superseded_at from verification_codes where purpose='CARE_LINK' and link_id=?", Timestamp.class, link));
    }
    @ParameterizedTest @ValueSource(longs = {600, 601})
    void expiryAndExactBoundaryAreInvalid(long advance) throws Exception {
        UUID recipient = account("PATIENT"), link = otpLink(recipient);
        clock.now = NOW.plusSeconds(advance);
        verify(actor, link, delivery.codes.get(recipient)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
    }
    @Test void resendOnlyChangesChallengesReplacesAndLimits() throws Exception {
        UUID recipient = account("PATIENT"), link = otpLink(recipient); String oldCode = delivery.codes.get(recipient);
        String before = jdbc.queryForObject("select row_to_json(c)::text from patient_caregivers c where id=?", String.class, link);
        resend(actor, link).andExpect(status().isAccepted()).andExpect(jsonPath("$.result").value("OTP_SENT"));
        assertEquals(before, jdbc.queryForObject("select row_to_json(c)::text from patient_caregivers c where id=?", String.class, link));
        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=? and superseded_at is not null", Integer.class, link));
        if (!oldCode.equals(delivery.codes.get(recipient))) verify(actor, link, oldCode).andExpect(status().isBadRequest());
        resend(actor, link).andExpect(status().isAccepted());
        resend(actor, link).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=? and consumed_at is null and superseded_at is null", Integer.class, link));
    }
    @Test void emailFailureInvalidatesChallengeRetainsPendingLinkAndConsumesQuota() throws Exception {
        UUID recipient = account("PATIENT"); profile(recipient, name, number, "CCCD"); delivery.fail = true;
        add(number, "CCCD", 503);
        UUID link = jdbc.queryForObject("select c.id from patient_caregivers c join patient_profiles p on p.id=c.dependent_patient_profile_id where p.full_name=?", UUID.class, name);
        assertEquals("REQUESTED", jdbc.queryForObject("select state from patient_caregivers where id=?", String.class, link));
        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=? and superseded_at is not null", Integer.class, link));
        verify(actor, link, "123456").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
        add(number, "CCCD", 409);
        resend(actor, link).andExpect(status().isServiceUnavailable());
        resend(actor, link).andExpect(status().isServiceUnavailable());
        resend(actor, link).andExpect(status().isTooManyRequests());
        assertEquals(3, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=? and superseded_at is not null", Integer.class, link));
        assertEquals(0, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=? and consumed_at is null and superseded_at is null", Integer.class, link));
    }
    @ParameterizedTest @NullSource @ValueSource(strings = {"PERSONAL_ID", "PASSPORT"})
    void branchCNonCccdHasNoChallenge(String type) throws Exception {
        UUID recipient = account("PATIENT"), target = profile(recipient, name, number, type);
        var result = add(number, type == null ? "CCCD" : type, 202); UUID link = id(result, "linkId");
        assertEquals("COUNTER_REQUIRED", result.get("result").asText()); assertFalse(access.canAccess(actor, target));
        assertEquals(0, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=?", Integer.class, link));
        assertFalse(delivery.codes.containsKey(recipient));
    }
    @Test void missingDocumentAndAccountlessExistingRequireCounter() throws Exception {
        UUID target = profile(null, name, null, null);
        var result = add(null, null, 202); assertEquals("COUNTER_REQUIRED", result.get("result").asText());
        assertFalse(access.canAccess(actor, target));
    }
    @Test void counterQueryConfirmAndRejection() throws Exception {
        UUID link = counterLink();
        mvc.perform(get("/api/v1/care-links/pending").param("query", number).with(actor(receptionist, "RECEPTIONIST")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].linkId").value(link.toString()));
        for (String checked : List.of("false", "null")) mvc.perform(post("/api/v1/care-links/{id}/counter-confirmation", link)
                .with(actor(receptionist, "RECEPTIONIST")).with(csrf()).contentType("application/json")
                .content("{\"identityChecked\":" + checked + "}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/care-links/{id}/counter-confirmation", link).with(actor(receptionist, "RECEPTIONIST")).with(csrf())
                .contentType("application/json").content("{\"identityChecked\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("LINKED"));
        assertEquals(receptionist, jdbc.queryForObject("select verified_by_account_id from patient_caregivers where id=?", UUID.class, link));
        assertEquals("COUNTER_CHECK", jdbc.queryForObject("select verification_method from patient_caregivers where id=?", String.class, link));
        name += " Reject"; number = UUID.randomUUID().toString().substring(0, 20); UUID rejected = counterLink();
        mvc.perform(post("/api/v1/care-links/{id}/counter-rejection", rejected).with(actor(receptionist, "RECEPTIONIST")).with(csrf()))
                .andExpect(status().isNoContent());
        assertEquals("REVOKED", jdbc.queryForObject("select state from patient_caregivers where id=?", String.class, rejected));
        assertEquals(receptionist, jdbc.queryForObject("select revoked_by_account_id from patient_caregivers where id=?", UUID.class, rejected));
    }
    @Test void wrongOwnerAndWrongPendingTypeDoNotDiscloseOtp() throws Exception {
        UUID recipient = account("PATIENT"), link = otpLink(recipient), stranger = account("PATIENT");
        profile(stranger, "Stranger", "stranger", "CCCD");
        verify(stranger, link, "123456").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("CARE_LINK_NOT_AVAILABLE"));
        resend(stranger, link).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/care-links/{id}/counter-confirmation", link).with(actor(receptionist, "RECEPTIONIST")).with(csrf())
                .contentType("application/json").content("{\"identityChecked\":true}")).andExpect(status().isConflict());
        name += " Counter"; number = UUID.randomUUID().toString().substring(0, 20); UUID counter = counterLink();
        verify(actor, counter, "123456").andExpect(status().isConflict());
        resend(actor, counter).andExpect(status().isConflict());
    }
    @Test void confirmedAdministrativeUpdateNoVersionPreservesAccountAndHistory() throws Exception {
        var result = add(number, "CCCD", 201); UUID target = id(result.get("profile"), "patientProfileId");
        mvc.perform(get("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.medicalHistory").doesNotExist()).andExpect(jsonPath("$.allergy").doesNotExist());
        String update = json.writeValueAsString(Map.of("fullName", name + " Changed", "dateOfBirth", "2000-01-02",
                "gender", "OTHER", "phone", "0912345678", "address", " HN ", "nationalId", number, "identityDocumentType", "CCCD"));
        mvc.perform(put("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(update)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.phoneCanonical").value("+84912345678"));
        assertNull(jdbc.queryForObject("select account_id from patient_profiles where id=?", UUID.class, target));
        assertNull(jdbc.queryForObject("select phone_owner_type from patient_profiles where id=?", String.class, target));
        UUID stranger = account("PATIENT"); profile(stranger, "Stranger", "s", "CCCD");
        mvc.perform(get("/api/v1/me/dependents/{id}", target).with(actor(stranger, "PATIENT"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/me/dependents/{id}", target).with(actor(stranger, "PATIENT")).with(csrf())
                .contentType("application/json").content(update)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf())).andExpect(status().isNoContent());
        assertFalse(access.canAccess(actor, target)); assertTrue(access.canAccess(actor, own));
        assertEquals(1, jdbc.queryForObject("select count(*) from patient_profiles where id=?", Integer.class, target));
        mvc.perform(get("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT"))).andExpect(status().isNotFound());
        name += " Changed"; add(number, "CCCD", 202);
        assertEquals(2, jdbc.queryForObject("select count(*) from patient_caregivers where requester_account_id=? and dependent_patient_profile_id=?", Integer.class, actor, target));
    }
    @Test void dependentAddPayloadIsClearedOnUseAndAfterExpiryByNextRequest() throws Exception {
        // Another account's request expired 10 minutes ago: kept, its owner may still resend.
        UUID other = account("PATIENT"), recentRow = UUID.randomUUID(), oldRow = UUID.randomUUID(), ownLockedRow = UUID.randomUUID();
        jdbc.update("insert into verification_codes(id,account_id,purpose,code_hash,payload,expires_at,created_at) values (?,?,'DEPENDENT_ADD','stale-hash',?::jsonb,?,?)",
                recentRow, other, body(name, number, "CCCD"), Timestamp.from(NOW.minusSeconds(600)), Timestamp.from(NOW.minusSeconds(1200)));
        // Another account's request expired two days ago: details dropped.
        jdbc.update("insert into verification_codes(id,account_id,purpose,code_hash,payload,expires_at,created_at,superseded_at) values (?,?,'DEPENDENT_ADD','stale-hash',?::jsonb,?,?,?)",
                oldRow, account("PATIENT"), body(name, number, "CCCD"), Timestamp.from(NOW.minusSeconds(172800)),
                Timestamp.from(NOW.minusSeconds(173700)), Timestamp.from(NOW.minusSeconds(173000)));
        // The actor's own request locked after 5 wrong codes (superseded, details kept for resend): dropped now.
        jdbc.update("insert into verification_codes(id,account_id,purpose,code_hash,payload,expires_at,created_at,superseded_at,failed_attempts) values (?,?,'DEPENDENT_ADD','stale-hash',?::jsonb,?,?,?,5)",
                ownLockedRow, actor, body(name, number, "CCCD"), Timestamp.from(NOW.plusSeconds(300)),
                Timestamp.from(NOW.minusSeconds(600)), Timestamp.from(NOW.minusSeconds(60)));
        var created = mvc.perform(post("/api/v1/me/dependents/add-requests").with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(body(name, number, "CCCD"))).andExpect(status().isAccepted()).andReturn();
        UUID request = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("requestId").asText());
        assertNotNull(jdbc.queryForObject("select payload::text from verification_codes where id=?", String.class, request));
        assertNotNull(jdbc.queryForObject("select payload::text from verification_codes where id=?", String.class, recentRow));
        assertNull(jdbc.queryForObject("select payload::text from verification_codes where id=?", String.class, oldRow));
        assertNull(jdbc.queryForObject("select payload::text from verification_codes where id=?", String.class, ownLockedRow));
        String code = delivery.codes.get(actor);
        mvc.perform(post("/api/v1/me/dependents/add-requests/{requestId}/verify", request).with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("code", code)))).andExpect(status().isCreated());
        assertNull(jdbc.queryForObject("select payload::text from verification_codes where id=?", String.class, request));
        assertNotNull(jdbc.queryForObject("select consumed_at from verification_codes where id=?", Timestamp.class, request));
    }
    @Test void dependentProfileEditKeepsMedicalHistoryAndItsVersion() throws Exception {
        UUID target = id(add(number, "CCCD", 201).get("profile"), "patientProfileId");
        mvc.perform(put("/api/v1/patient-profiles/{id}/medical-history", target).with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(historyBody(0))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        String edit = json.writeValueAsString(Map.of("fullName", name + " Edited", "dateOfBirth", "2000-01-02", "gender", "OTHER",
                "phone", "0912345678", "address", " HN ", "nationalId", number, "identityDocumentType", "CCCD"));
        mvc.perform(put("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(edit)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertEquals("Tien su", jdbc.queryForObject("select conditions from patient_profiles where id=?", String.class, target));
        mvc.perform(put("/api/v1/patient-profiles/{id}/medical-history", target).with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(historyBody(1))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
    }
    String historyBody(long version) throws Exception {
        return json.writeValueAsString(Map.of("conditions", "Tien su", "allergies", "", "procedures", "", "injuries", "",
                "orthopedicHistory", "", "version", version));
    }
    @Test void matchingNormalizesLegacyNamePreservesAccentsAndRejectsAmbiguity() throws Exception {
        profile(null, "  " + java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replace(" ", "   ") + "  ", number, null);
        var result = add(number, "CCCD", 202); assertEquals("COUNTER_REQUIRED", result.get("result").asText());
        add(number, "CCCD", 409);
        profile(null, name, number, "CCCD"); add(number, "CCCD", 409);
        name = "Nguyen An " + UUID.randomUUID(); number = UUID.randomUUID().toString().substring(0, 20);
        profile(null, name.replace("Nguyen", "Nguyễn"), number, "CCCD");
        add(number, "CCCD", 201);
    }
    @Test void selectorSelfFirstStableAndRevokedAbsent() throws Exception {
        var a = add(number, "CCCD", 201); UUID revoked = id(a, "linkId");
        mvc.perform(delete("/api/v1/me/dependents/{id}", id(a.get("profile"), "patientProfileId"))
                .with(actor(actor, "PATIENT")).with(csrf())).andExpect(status().isNoContent());
        name += " Confirmed"; number = UUID.randomUUID().toString().substring(0, 20);
        UUID confirmed = id(add(number, "CCCD", 201), "linkId");
        name += " Counter"; number = UUID.randomUUID().toString().substring(0, 20); UUID counter = counterLink();
        name += " OTP"; number = UUID.randomUUID().toString().substring(0, 20); UUID otp = otpLink(account("PATIENT"));
        var response = mvc.perform(get("/api/v1/me/patient-profiles").with(actor(actor, "PATIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].relationship").value("SELF"))
                .andExpect(jsonPath("$.items[0].linkId").isEmpty()).andReturn();
        var items = json.readTree(response.getResponse().getContentAsString()).get("items");
        assertEquals(4, items.size());
        assertEquals("CONFIRMED", items.get(0).get("linkStatus").asText());
        List<String> expected = List.of(confirmed, counter, otp).stream().map(UUID::toString).sorted().toList();
        List<String> actual = new ArrayList<>();
        Map<String, String> statuses = new HashMap<>();
        for (int i = 1; i < items.size(); i++) {
            String link = items.get(i).get("linkId").asText(); actual.add(link);
            statuses.put(link, items.get(i).get("linkStatus").asText());
        }
        assertEquals(expected, actual);
        assertEquals("CONFIRMED", statuses.get(confirmed.toString()));
        assertEquals("PENDING_OTP", statuses.get(otp.toString()));
        assertEquals("PENDING_COUNTER", statuses.get(counter.toString()));
        assertFalse(items.toString().contains(revoked.toString()));
    }
    @ParameterizedTest @EnumSource(AccountRole.class)
    void endpointRoleMatrix(AccountRole role) throws Exception {
        UUID roleAccount = account(role.name()); if (role == AccountRole.PATIENT) profile(roleAccount, "Role", "role", "CCCD");
        mvc.perform(get("/api/v1/me/patient-profiles").with(actor(roleAccount, role.name())))
                .andExpect(status().is(role == AccountRole.PATIENT ? 200 : 403));
        mvc.perform(get("/api/v1/care-links/pending").with(actor(roleAccount, role.name())))
                .andExpect(status().is(role == AccountRole.RECEPTIONIST ? 200 : 403));
        if (role != AccountRole.PATIENT) {
            mvc.perform(post("/api/v1/me/dependents").with(actor(roleAccount, role.name())).with(csrf())
                    .contentType("application/json").content(body(name, number, "CCCD"))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/me/dependents/links/{id}/otp/verify", UUID.randomUUID()).with(actor(roleAccount, role.name())).with(csrf())
                    .contentType("application/json").content("{\"code\":\"123456\"}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/me/dependents/links/{id}/otp/resend", UUID.randomUUID()).with(actor(roleAccount, role.name())).with(csrf())).andExpect(status().isForbidden());
            for (var builder : List.of(get("/api/v1/me/dependents/{id}", UUID.randomUUID()),
                    put("/api/v1/me/dependents/{id}", UUID.randomUUID()).contentType("application/json").content(body(name, number, "CCCD")),
                    delete("/api/v1/me/dependents/{id}", UUID.randomUUID())))
                mvc.perform(builder.with(actor(roleAccount, role.name())).with(csrf())).andExpect(status().isForbidden());
        }
        if (role != AccountRole.RECEPTIONIST) {
            mvc.perform(post("/api/v1/care-links/{id}/counter-confirmation", UUID.randomUUID()).with(actor(roleAccount, role.name())).with(csrf())
                    .contentType("application/json").content("{\"identityChecked\":true}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/care-links/{id}/counter-rejection", UUID.randomUUID()).with(actor(roleAccount, role.name())).with(csrf())).andExpect(status().isForbidden());
        }
    }
    @Test void inactiveAndAnonymousFailClosedWithoutWeakeningCsrf() throws Exception {
        jdbc.update("update accounts set status='DISABLED' where id in (?,?)", actor, receptionist);
        assertFalse(access.canAccess(actor, own));
        mvc.perform(get("/api/v1/me/patient-profiles").with(actor(actor, "PATIENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/care-links/pending").with(actor(receptionist, "RECEPTIONIST"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/dependents").with(csrf()).contentType("application/json").content(body(name, number, "CCCD")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/dependents").contentType("application/json").content(body(name, number, "CCCD")))
                .andExpect(status().isForbidden());
    }
    @Test void concurrentSameTupleCreatesOneProfileAndLiveLink() throws Exception {
        var statuses = race(() -> mvc.perform(post("/api/v1/me/dependents").with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(body(name, number, "CCCD"))).andReturn().getResponse().getStatus());
        assertEquals(List.of(201, 409), statuses.stream().sorted().toList());
        assertEquals(1, jdbc.queryForObject("select count(*) from patient_profiles where full_name=?", Integer.class, name));
    }
    @Test void concurrentVerifyConsumesOnlyOnce() throws Exception {
        UUID recipient = account("PATIENT"), link = otpLink(recipient); String code = delivery.codes.get(recipient);
        var statuses = race(() -> verify(actor, link, code).andReturn().getResponse().getStatus());
        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=? and consumed_at is not null", Integer.class, link));
    }
    List<Integer> race(Callable<Integer> action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Integer> task = () -> { assertTrue(gate.await(10, TimeUnit.SECONDS)); return action.call(); };
            var first = executor.submit(task); var second = executor.submit(task); gate.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }
    @Test void actualAppointmentPostUsesRealDependentAuthorization() throws Exception {
        UUID target = id(add(number, "CCCD", 201).get("profile"), "patientProfileId");
        String booking = bookingRequest(target);
        var booked = mvc.perform(post("/api/v1/appointments").with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(booking)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientProfileId").value(target.toString())).andReturn();
        UUID appointment = UUID.fromString(json.readTree(booked.getResponse().getContentAsString()).get("id").asText());
        assertEquals(actor, jdbc.queryForObject("select created_by_account_id from appointments where id=?", UUID.class, appointment));
        mvc.perform(delete("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/appointments").with(actor(actor, "PATIENT")).with(csrf()).contentType("application/json").content(booking))
                .andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("select count(*) from appointments where id=?", Integer.class, appointment));
        for (boolean otp : List.of(false, true)) {
            name += " Pending"; number = UUID.randomUUID().toString().substring(0, 20);
            UUID pendingProfile = profile(otp ? account("PATIENT") : null, name, number, "CCCD"); add(number, "CCCD", 202);
            mvc.perform(post("/api/v1/appointments").with(actor(actor, "PATIENT")).with(csrf()).contentType("application/json")
                    .content(booking.replace(target.toString(), pendingProfile.toString()))).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/v1/appointments").with(actor(actor, "PATIENT")).with(csrf()).contentType("application/json")
                .content(booking.replace(target.toString(), own.toString()).replace("03:00:00", "04:00:00"))).andExpect(status().isCreated());
    }
    String bookingRequest(UUID target) {
        UUID doctorAccount = account("DOCTOR"), doctor = UUID.randomUUID(), clinicService = UUID.randomUUID();
        jdbc.update("insert into clinic_services(id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?,'KAN26 Booking',30,0,true,true)", clinicService);
        jdbc.update("insert into staff_profiles(is_doctor, id,account_id,display_name,photo_url,work_contact,specialties,qualifications,biography,consultation_information,publicly_visible,accepting_new_bookings,in_active_directory) "
                + "values (true, ?,?,'KAN26 Doctor','','','','','','',true,true,true)", doctor, doctorAccount);
        jdbc.update("insert into doctor_services(doctor_profile_id,service_id,active) values (?,?,true)", doctor, clinicService);
        jdbc.update("insert into work_shifts(id,staff_profile_id,starts_at,ends_at) values (?,?,?,?)", UUID.randomUUID(), doctor,
                Timestamp.from(Instant.parse("2026-10-16T02:00:00Z")), Timestamp.from(Instant.parse("2026-10-16T05:00:00Z")));
        return json.writeValueAsString(Map.of("patientProfileId", target, "serviceId", clinicService, "doctorId", doctor,
                "startTime", "2026-10-16T03:00:00Z", "purpose", "Routine visit"));
    }
    @Test void revokeVersusBookingUsesSameCoordinationGuard() throws Exception {
        UUID target = id(add(number, "CCCD", 201).get("profile"), "patientProfileId");
        String booking = bookingRequest(target);
        var outcomes = raceTwo(() -> mvc.perform(post("/api/v1/appointments").with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(booking)).andReturn().getResponse().getStatus(),
                () -> mvc.perform(delete("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf()))
                        .andReturn().getResponse().getStatus());
        assertTrue(List.of(201, 404).contains(outcomes.getFirst())); assertEquals(204, outcomes.getLast());
        assertFalse(access.canAccess(actor, target));
        assertEquals(outcomes.getFirst() == 201 ? 1 : 0,
                jdbc.queryForObject("select count(*) from appointments where patient_profile_id=?", Integer.class, target));
        assertEquals(1, jdbc.queryForObject("select count(*) from patient_profiles where id=?", Integer.class, target));
    }
    @Test void upgradeWithLaterMigrationsPreservesLegacyAndEnforcesR3History() throws Exception {
        String schema = "kan26_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var old = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("48").load(); old.migrate();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), p = UUID.randomUUID(), link = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'PATIENT','ACTIVE',now(),now()),(?,?,'PATIENT','ACTIVE',now(),now())",
                first, first + "@example.test", second, second + "@example.test");
        jdbc.update("insert into " + schema + ".patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'Legacy','+84912345678','PATIENT','2000-01-02','OTHER')", p, second);
        jdbc.update("insert into " + schema + ".patient_caregivers(id,requester_account_id,dependent_patient_profile_id,confirming_account_id,state,requested_at,confirmed_at) values (?,?,?,?,'CONFIRMED',now(),now())", link, first, p, second);
        // Install later versions first, then resolve the new lower-number migration out of order.
        // V111 reads care_link_otp_challenges, which V049 creates, so it waits until V049 is applied.
        var migrationDirectory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"), "kan26-later-");
        try (var paths = java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/db/migration"))) {
            for (var migration : paths.filter(pth -> !pth.getFileName().toString().matches("V(049|111)__.*")).toList())
                java.nio.file.Files.copy(migration, migrationDirectory.resolve(migration.getFileName()));
        }
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("filesystem:" + migrationDirectory.toAbsolutePath()).load().migrate();
        var migrated = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .outOfOrder(true).target("49").load().migrate();
        assertEquals(1, migrated.migrationsExecuted);
        assertEquals(1, Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).outOfOrder(true).load().migrate().migrationsExecuted);
        assertNull(jdbc.queryForObject("select relationship from " + schema + ".patient_caregivers where id=?", String.class, link));
        assertNull(jdbc.queryForObject("select verified_at from " + schema + ".patient_caregivers where id=?", Timestamp.class, link));
        assertThrows(Exception.class, () -> jdbc.update("delete from " + schema + ".patient_caregivers where id=?", link));
        assertThrows(Exception.class, () -> jdbc.update("update " + schema + ".patient_caregivers set relationship='CHILD' where id=?", link));
        jdbc.update("update " + schema + ".patient_caregivers set state='REVOKED',revoked_at=now(),revoked_by_account_id=? where id=?", first, link);
        assertThrows(Exception.class, () -> jdbc.update("update " + schema + ".patient_caregivers set state='CONFIRMED' where id=?", link));
    }

    @Test void missingEmailRequiresCounterWithoutChallenge() throws Exception {
        UUID recipient = account("PATIENT"); jdbc.update("update accounts set normalized_email='' where id=?", recipient);
        UUID target = profile(recipient, name, number, "CCCD");
        var result = add(number, "CCCD", 202); assertEquals("COUNTER_REQUIRED", result.get("result").asText());
        assertFalse(access.canAccess(actor, target)); assertFalse(delivery.codes.containsKey(recipient));
    }
    @Test void twoRequestersCanLinkSameDependentButSelfAndWrongDocumentsAreDenied() throws Exception {
        UUID target = profile(null, name, number, "CCCD"); UUID link = id(add(number, "CCCD", 202), "linkId");
        UUID second = account("PATIENT"); profile(second, "Second requester", "second", "CCCD");
        mvc.perform(post("/api/v1/me/dependents").with(actor(second, "PATIENT")).with(csrf()).contentType("application/json")
                .content(body(name, number, "CCCD"))).andExpect(status().isAccepted());
        assertFalse(access.canAccess(second, target)); assertFalse(access.canAccess(actor, target));
        mvc.perform(post("/api/v1/me/dependents").with(actor(actor, "PATIENT")).with(csrf()).contentType("application/json")
                .content(body("Self " + actor, actor.toString().substring(0, 20), "CCCD"))).andExpect(status().isConflict());
        // A document-type mismatch must not be mistaken for a new profile.
        add(number, "PASSPORT", 409);
        assertEquals(2, jdbc.queryForObject("select count(*) from patient_caregivers where dependent_patient_profile_id=?", Integer.class, target));
        assertNotNull(link);
    }
    @Test void pendingCannotReadUpdateOrRevokeAndNameSearchStaysInsidePendingSet() throws Exception {
        UUID link = counterLink(); UUID target = jdbc.queryForObject("select dependent_patient_profile_id from patient_caregivers where id=?", UUID.class, link);
        mvc.perform(get("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf()).contentType("application/json")
                .content(body(name, number, "CCCD"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/care-links/pending").param("query", name.toUpperCase(Locale.ROOT)).with(actor(receptionist, "RECEPTIONIST")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].linkId").value(link.toString()));
        profile(null, "Outside " + actor, "outside", "CCCD");
        mvc.perform(get("/api/v1/care-links/pending").param("query", "Outside " + actor).with(actor(receptionist, "RECEPTIONIST")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }
    @Test void accountLinkedUpdatePreservesAccountPhoneOwnerAndOptimisticVersion() throws Exception {
        UUID recipient = account("PATIENT"), target = profile(recipient, name, number, "CCCD"), link = id(add(number, "CCCD", 202), "linkId");
        verify(actor, link, delivery.codes.get(recipient)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("fullName", name, "dateOfBirth", "2000-01-02", "gender", "OTHER",
                        "phone", "0987654321", "nationalId", number, "identityDocumentType", "CCCD"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasAccount").value(true)).andExpect(jsonPath("$.version").value(1));
        assertEquals(recipient, jdbc.queryForObject("select account_id from patient_profiles where id=?", UUID.class, target));
        assertEquals("PATIENT", jdbc.queryForObject("select phone_owner_type from patient_profiles where id=?", String.class, target));
        var directory = applicationProfileDirectory();
        var stale = directory.findProfile(target).orElseThrow();
        jdbc.update("update patient_profiles set version=version+1 where id=?", target);
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> directory.update(stale, stale.version()));
    }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory profileDirectory;
    fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory applicationProfileDirectory() { return profileDirectory; }

    @Test void dependentPutRealConcurrentPersistenceConflictReturns409() throws Exception {
        UUID target = id(add(number, "CCCD", 201).get("profile"), "patientProfileId");
        org.mockito.Mockito.doAnswer(invocation -> {
            fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord record = invocation.getArgument(0);
            if (target.equals(record.id())) {
                // Interleave an independently committed administrative write after the HTTP use case's read.
                CompletableFuture.runAsync(() -> jdbc.update("update patient_profiles set version=version+1 where id=?", target))
                        .get(5, TimeUnit.SECONDS);
            }
            return invocation.callRealMethod();
        }).when(profileDirectory).update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        try {
            mvc.perform(put("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf())
                    .contentType("application/json").content(body(name + " updated", number, "CCCD")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
        } finally { org.mockito.Mockito.reset(profileDirectory); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void slowCareSenderDoesNotBlockUnrelatedRealBooking(boolean resending) throws Exception {
        UUID recipient = account("PATIENT"); profile(recipient, name, number, "CCCD");
        UUID link = resending ? id(add(number, "CCCD", 202), "linkId") : null;
        UUID other = account("PATIENT"), otherProfile = profile(other, "Other booking patient", "other", "CCCD");
        String booking = bookingRequest(otherProfile);
        var gate = new DeliveryGate(recipient, false); delivery.gate.set(gate);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var care = executor.submit(() -> resending ? resend(actor, link).andReturn().getResponse().getStatus()
                    : mvc.perform(post("/api/v1/me/dependents").with(actor(actor, "PATIENT")).with(csrf())
                            .contentType("application/json").content(body(name, number, "CCCD"))).andReturn().getResponse().getStatus());
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS), "Care must reach the blocking sender");
                UUID pendingLink = jdbc.queryForObject("select c.id from patient_caregivers c join patient_profiles p on p.id=c.dependent_patient_profile_id where p.full_name=?", UUID.class, name);
                // The delivery code is not enabled in persistence before the network call succeeds.
                String wrong = "000000".equals(gate.code) ? "111111" : "000000";
                verify(actor, pendingLink, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("OTP_INVALID"));
                UUID challenge = jdbc.queryForObject("select id from verification_codes where purpose='CARE_LINK' and link_id=? and consumed_at is null and superseded_at is null", UUID.class, pendingLink);
                assertEquals(1, jdbc.queryForObject("select failed_attempts from verification_codes where purpose='CARE_LINK' and id=?", Integer.class, challenge));
                var booked = executor.submit(() -> mvc.perform(post("/api/v1/appointments").with(actor(other, "PATIENT")).with(csrf())
                        .contentType("application/json").content(booking)).andReturn().getResponse().getStatus());
                assertEquals(201, booked.get(4, TimeUnit.SECONDS));
                assertEquals(1, gate.release.getCount(), "Booking must finish while SMTP is still blocked");
                gate.release.countDown(); assertEquals(202, care.get(5, TimeUnit.SECONDS));
                assertEquals(1, jdbc.queryForObject("select failed_attempts from verification_codes where purpose='CARE_LINK' and id=?", Integer.class, challenge),
                        "Activation must preserve attempts committed while SMTP was blocked");
                verify(actor, pendingLink, gate.code).andExpect(status().isOk());
            } finally { gate.release.countDown(); }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void earlierDeliveryCannotMutateSuccessfulReplacement(boolean failedDelivery) throws Exception {
        UUID recipient = account("PATIENT"); profile(recipient, name, number, "CCCD");
        var gate = new DeliveryGate(recipient, failedDelivery); delivery.gate.set(gate);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var original = executor.submit(() -> mvc.perform(post("/api/v1/me/dependents").with(actor(actor, "PATIENT")).with(csrf())
                    .contentType("application/json").content(body(name, number, "CCCD"))).andReturn().getResponse().getStatus());
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS));
                UUID link = jdbc.queryForObject("select c.id from patient_caregivers c join patient_profiles p on p.id=c.dependent_patient_profile_id where p.full_name=?", UUID.class, name);
                UUID originalChallenge = jdbc.queryForObject("select id from verification_codes where purpose='CARE_LINK' and link_id=? and superseded_at is null", UUID.class, link);
                resend(actor, link).andExpect(status().isAccepted()); String replacement = delivery.codes.get(recipient);
                String wrong = "000000".equals(replacement) ? "111111" : "000000";
                verify(actor, link, wrong).andExpect(status().isBadRequest());
                UUID replacementChallenge = jdbc.queryForObject("select id from verification_codes where purpose='CARE_LINK' and link_id=? and consumed_at is null and superseded_at is null", UUID.class, link);
                assertNotEquals(originalChallenge, replacementChallenge);
                assertEquals(1, jdbc.queryForObject("select failed_attempts from verification_codes where purpose='CARE_LINK' and id=?", Integer.class, replacementChallenge));
                String before = jdbc.queryForObject("select row_to_json(c)::text from verification_codes c where c.purpose='CARE_LINK' and id=?", String.class, replacementChallenge);
                // Successful old SMTP reaches activation, which must update zero eligible rows (409).
                gate.release.countDown(); assertEquals(failedDelivery ? 503 : 409, original.get(5, TimeUnit.SECONDS));
                String after = jdbc.queryForObject("select row_to_json(c)::text from verification_codes c where c.purpose='CARE_LINK' and id=?", String.class, replacementChallenge);
                assertTrue(Objects.equals(before, after), "Old delivery must not change replacement hash, attempts or timestamps");
                assertNotNull(jdbc.queryForObject("select superseded_at from verification_codes where purpose='CARE_LINK' and id=?", Timestamp.class, originalChallenge));
                assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=? and consumed_at is null and superseded_at is null", Integer.class, link));
                verify(actor, link, replacement).andExpect(status().isOk());
            } finally { gate.release.countDown(); }
        }
    }

    @Test void differentLinksForSameRecipientMayBothHaveCurrentCodes() throws Exception {
        UUID recipient = account("PATIENT"), firstOwner = actor;
        UUID firstLink = otpLink(recipient);
        actor = account("PATIENT"); profile(actor, "Second requester", actor.toString().substring(0, 20), "CCCD");
        UUID secondLink = id(add(number, "CCCD", 202), "linkId");
        assertNotEquals(firstLink, secondLink);
        assertEquals(2, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and account_id=? and consumed_at is null and superseded_at is null", Integer.class, recipient));
        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=? and consumed_at is null and superseded_at is null", Integer.class, firstLink));
        assertEquals(1, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=? and consumed_at is null and superseded_at is null", Integer.class, secondLink));
        assertNotEquals(firstOwner, actor);
    }

    @Test void careLinkRateLimitExcludesEmailVerificationCodes() throws Exception {
        UUID recipient = account("PATIENT"), link = otpLink(recipient);
        for (int i = 0; i < 3; i++) {
            jdbc.update("insert into verification_codes(id,account_id,purpose,code_hash,created_at,expires_at,consumed_at) values (?,?,'EMAIL_VERIFY','hash',?,?,?)",
                    UUID.randomUUID(), recipient, Timestamp.from(NOW.minusSeconds(30)), Timestamp.from(NOW.plusSeconds(600)), Timestamp.from(NOW.minusSeconds(1)));
        }
        resend(actor, link).andExpect(status().isAccepted());
        assertEquals(2, jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and account_id=?", Integer.class, recipient));
    }

    @Test void constraintsRejectInvalidMetadataHistoryAndSecondCurrentChallenge() throws Exception {
        UUID recipient = account("PATIENT"), link = otpLink(recipient);
        assertThrows(Exception.class, () -> jdbc.update("insert into verification_codes(id,link_id,account_id,code_hash,expires_at,created_at,purpose) values (?,?,?,'hash',?,?,'CARE_LINK')",
                UUID.randomUUID(), link, recipient, Timestamp.from(NOW.plusSeconds(600)), Timestamp.from(NOW)));
        assertThrows(Exception.class, () -> jdbc.update("update verification_codes set failed_attempts=6 where purpose='CARE_LINK' and link_id=?", link));
        assertThrows(Exception.class, () -> jdbc.update("update verification_codes set expires_at=created_at where purpose='CARE_LINK' and link_id=?", link));
        assertThrows(Exception.class, () -> jdbc.update("update verification_codes set consumed_at=created_at-interval '1 second' where purpose='CARE_LINK' and link_id=?", link));
        assertThrows(Exception.class, () -> jdbc.update("update verification_codes set superseded_at=created_at-interval '1 second' where purpose='CARE_LINK' and link_id=?", link));
        assertThrows(Exception.class, () -> jdbc.update("update patient_caregivers set relationship='PARENT' where id=?", link));
        assertThrows(Exception.class, () -> jdbc.update("update patient_caregivers set pending_reason='COUNTER' where id=?", link));
        assertThrows(Exception.class, () -> jdbc.update("update patient_caregivers set state='CONFIRMED',confirmed_at=requested_at where id=?", link));
        verify(actor, link, delivery.codes.get(recipient)).andExpect(status().isOk());
        assertThrows(Exception.class, () -> jdbc.update("update patient_caregivers set state='REVOKED',revoked_at=now(),revoked_by_account_id=?,verified_at=verified_at+interval '1 second' where id=?", actor, link));
        assertThrows(Exception.class, () -> jdbc.update("delete from patient_caregivers where id=?", link));
        assertThrows(Exception.class, () -> jdbc.update("insert into patient_caregivers(id,requester_account_id,dependent_patient_profile_id,state,requested_at) values (?,?,?,'REQUESTED',now())",
                UUID.randomUUID(), actor, own));
    }
    @Test void resendVersusVerifyLeavesOneCoherentOutcome() throws Exception {
        UUID recipient = account("PATIENT"), link = otpLink(recipient); String original = delivery.codes.get(recipient);
        var results = raceTwo(() -> verify(actor, link, original).andReturn().getResponse().getStatus(),
                () -> resend(actor, link).andReturn().getResponse().getStatus());
        assertTrue(List.of(200, 400).contains(results.getFirst()));
        assertTrue(List.of(202, 409).contains(results.getLast()));
        if (results.getFirst() == 200) assertEquals("CONFIRMED", jdbc.queryForObject("select state from patient_caregivers where id=?", String.class, link));
        else {
            assertEquals(202, results.getLast());
            assertEquals("REQUESTED", jdbc.queryForObject("select state from patient_caregivers where id=?", String.class, link));
        }
        assertTrue(jdbc.queryForObject("select count(*) from verification_codes where purpose='CARE_LINK' and link_id=? and consumed_at is null and superseded_at is null", Integer.class, link) <= 1);
    }
    @Test void revokeVersusUpdateCannotWriteAfterRevocation() throws Exception {
        UUID target = id(add(number, "CCCD", 201).get("profile"), "patientProfileId");
        var results = raceTwo(() -> mvc.perform(put("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf())
                .contentType("application/json").content(body(name, number, "CCCD"))).andReturn().getResponse().getStatus(),
                () -> mvc.perform(delete("/api/v1/me/dependents/{id}", target).with(actor(actor, "PATIENT")).with(csrf())).andReturn().getResponse().getStatus());
        assertTrue(List.of(200, 404).contains(results.getFirst())); assertEquals(204, results.getLast());
        assertFalse(access.canAccess(actor, target));
    }
    List<Integer> raceTwo(Callable<Integer> firstAction, Callable<Integer> secondAction) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first = executor.submit(() -> { assertTrue(gate.await(10, TimeUnit.SECONDS)); return firstAction.call(); });
            var second = executor.submit(() -> { assertTrue(gate.await(10, TimeUnit.SECONDS)); return secondAction.call(); });
            gate.countDown(); return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }
    @Test void generatedOpenApiDocumentsAllR3RoutesWithoutChangingOtherApis() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var document = json.readTree(response.getResponse().getContentAsString()); var paths = document.get("paths");
        for (String path : List.of("/api/v1/me/dependents", "/api/v1/me/patient-profiles", "/api/v1/me/dependents/{profileId}",
                "/api/v1/me/dependents/links/{linkId}/otp/verify", "/api/v1/me/dependents/links/{linkId}/otp/resend",
                "/api/v1/care-links/pending", "/api/v1/care-links/{linkId}/counter-confirmation", "/api/v1/care-links/{linkId}/counter-rejection"))
            assertNotNull(paths.get(path), path);
        assertNotNull(paths.get("/api/v1/me/dependents").get("post").get("responses").get("201"));
        assertNotNull(paths.get("/api/v1/me/dependents").get("post").get("responses").get("202"));
        assertNotNull(paths.get("/api/v1/me/dependents").get("post").get("security"));
        assertNotNull(paths.get("/api/v1/patient-profiles")); assertNotNull(paths.get("/api/v1/appointments"));
    }
    static class TestClock extends Clock {
        volatile Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    static class RecordingEmail implements EmailDeliveryService {
        final Map<UUID,String> codes = new ConcurrentHashMap<>(), addresses = new ConcurrentHashMap<>();
        final java.util.concurrent.atomic.AtomicReference<DeliveryGate> gate = new java.util.concurrent.atomic.AtomicReference<>();
        volatile boolean fail;
        public void sendVerificationCode(String email, String code, UUID account) {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),
                    "SMTP must never run in a database transaction");
            DeliveryGate blocking = gate.get();
            if (blocking != null && blocking.recipient.equals(account) && gate.compareAndSet(blocking, null)) {
                blocking.code = code; blocking.entered.countDown();
                try {
                    if (!blocking.release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test sender release timed out");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException("Test sender interrupted");
                }
                if (blocking.fail) throw new IllegalStateException("Test delivery unavailable");
            }
            if (fail) throw new IllegalStateException("Test delivery unavailable");
            codes.put(account, code); addresses.put(account, email);
        }
    }
    static class DeliveryGate {
        final UUID recipient;
        final boolean fail;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile String code;
        DeliveryGate(UUID recipient, boolean fail) { this.recipient = recipient; this.fail = fail; }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TestBindings {
        @Bean @Primary TestClock careClock() { return new TestClock(); }
        @Bean @Primary RecordingEmail careEmail() { return new RecordingEmail(); }
    }
}
