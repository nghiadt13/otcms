package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.exception.GoogleAuthenticationException;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@LocalPostgresTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class Kan29MyAccountPostgresIntegrationTest {
    static final String SCHEMA = "kan29_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        String ephemeral = UUID.randomUUID().toString() + UUID.randomUUID();
        r.add("spring.flyway.schemas", () -> SCHEMA);
        r.add("spring.flyway.default-schema", () -> SCHEMA);
        r.add("spring.datasource.hikari.schema", () -> SCHEMA);
        r.add("app.auth.session-security.jwt-secret", () -> ephemeral);
        r.add("app.auth.rate-limit-hmac-secret", () -> ephemeral);
    }
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired AuthSessionService sessions;
    @Autowired PasswordEncoder encoder;
    @Autowired EmailChangeService emails;
    @Autowired GoogleAuthenticationService googleAuthentication;
    @Autowired ExternalIdentityRepository identities;
    @Autowired AuthSessionRepository authSessions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailDeliveryService delivery;
    @MockitoBean GoogleOidcService google;
    final Map<String, String> codes = new ConcurrentHashMap<>();
    final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    UUID patient;
    String token;
    @BeforeEach void setup() {
        patient = account(AccountType.PATIENT, false);
        token = token(patient);
        doAnswer(invocation -> { codes.put(invocation.getArgument(0), invocation.getArgument(1)); return null; })
                .when(delivery).sendVerificationCode(anyString(), anyString(), any());
    }

    @ParameterizedTest @EnumSource(AccountType.class)
    void projectionAndPatchForEveryRole(AccountType type) throws Exception {
        UUID id = account(type, false);
        String bearer = token(id);
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.accountType").value(type.name()))
                .andExpect(jsonPath("$.kind").value(type == AccountType.PATIENT ? "PATIENT" : "STAFF"))
                .andExpect(jsonPath("$.role").value(switch (type) {
                    case OWNER -> "CLINIC_OWNER";
                    case DOCTOR -> "ORTHOPEDIC_DOCTOR";
                    default -> type.name();
                }))
                .andExpect(jsonPath("$.username").value("user-" + id))
                .andExpect(jsonPath("$.mustChangePassword").value(false)).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.avatarUrl").isEmpty())
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItems("updateDisplayName", "updateAvatar", "changePassword")))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("removeAvatar"))))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.failedCredentialCount").doesNotExist())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist()).andExpect(jsonPath("$.sessionId").doesNotExist());
        mvc.perform(patch("/api/v1/me").with(csrf()).header("Authorization", "Bearer " + bearer)
                .contentType("application/json").content("{\"displayName\":\"  Updated name  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Updated name"))
                .andExpect(jsonPath("$.email").exists()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + bearer))
                .andExpect(jsonPath("$.displayName").value("Updated name"));
    }

    @Test void explicitDisplayNameWinsAndLegacyPatientRouteSurvives() throws Exception {
        profile(patient);
        mvc.perform(patch("/api/v1/me").with(csrf()).header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"displayName\":\"Account name\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.displayName").value("Account name"))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItem("changeEmail")));
        mvc.perform(get("/api/v1/me/patient-account").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Account name"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "a", "  ", "toolong"})
    void invalidName(String value) throws Exception {
        if (value.equals("toolong")) value = "a".repeat(101);
        mvc.perform(patch("/api/v1/me").with(csrf()).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json.writeValueAsString(Map.of("displayName", value))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-08"));
    }

    @Test void unicodeNameCountsCodePointsAndRejectsUnknownFields() throws Exception {
        String name = "\uD83D\uDE00".repeat(100);
        mvc.perform(patch("/api/v1/me").with(csrf()).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json.writeValueAsString(Map.of("displayName", name))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value(name));
        mvc.perform(patch("/api/v1/me").with(csrf()).header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"displayName\":\"Name\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"ADMIN", "RECEPTIONIST", "DOCTOR", "IMAGING_TECHNICIAN", "LAB_TECHNICIAN"})
    void forcedPasswordAllowsOnlyExactMeRead(AccountType type) throws Exception {
        UUID id = account(type, true);
        String bearer = token(id);
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.allowedActions").isArray())
                .andExpect(jsonPath("$.allowedActions", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.allowedActions[0]").value("changePassword"));
        for (var request : List.of(patch("/api/v1/me").contentType("application/json").content("{\"displayName\":\"Name\"}"),
                delete("/api/v1/me/avatar"), post("/api/v1/me/email-change"), post("/api/v1/me/email-change/verify"),
                put("/api/v1/me/patient-profile"), get("/api/v1/me/patient-profile"),
                get("/api/v1/accounts/" + patient + "/avatar"), get("/api/v1/me/"))) {
            mvc.perform(request.with(csrf()).header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
        }
        mvc.perform(multipart("/api/v1/me/avatar").file(new MockMultipartFile("photo", "a.png", "image/png", png(8)))
                .with(r -> { r.setMethod("PUT"); return r; }).with(csrf()).header("Authorization", "Bearer " + bearer))
                .andExpect(status().isForbidden());
        jdbc.update("update accounts set must_change_password=false where id=?", id);
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + bearer))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItem("updateDisplayName")));
    }

    @ParameterizedTest @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    void avatarRoundTripAndIdempotentDelete(String type) throws Exception {
        byte[] bytes = switch (type) {
            case "image/jpeg" -> new byte[] {(byte)255, (byte)216, (byte)255, 1};
            case "image/png" -> png(8);
            default -> new byte[] {'R','I','F','F',4,0,0,0,'W','E','B','P'};
        };
        upload(bytes, type).andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("/api/v1/accounts/" + patient + "/avatar"))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItem("removeAvatar")));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.avatarUrl").isNotEmpty());
        String other = token(account(AccountType.ADMIN, false));
        mvc.perform(get("/api/v1/accounts/" + patient + "/avatar").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes)).andExpect(content().contentType(type));
        for (int i=0;i<2;i++) mvc.perform(delete("/api/v1/me/avatar").with(csrf()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.avatarUrl").isEmpty());
        mvc.perform(get("/api/v1/accounts/" + patient + "/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test void avatarSizeAndMimeBoundaries() throws Exception {
        upload(png(2097152), "image/png").andExpect(status().isOk());
        for (var bytes : List.of(png(2097153), new byte[0], new byte[] {1,2,3}))
            upload(bytes, "image/png").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-08"));
        upload(png(8), "text/plain").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-08"));
        upload(png(8), "image/jpeg").andExpect(status().isBadRequest());
    }

    @Test void emailChangeStoresOnlyHashAndKeepsOldEmailUntilConsumed() throws Exception {
        String old = accounts.findById(patient).orElseThrow().getNormalizedEmail(), target = email();
        requestEmail(target).andExpect(status().isAccepted());
        assertEquals(old, accounts.findById(patient).orElseThrow().getNormalizedEmail());
        String code = codes.get(target); assertNotNull(code); assertTrue(code.matches("[0-9]{6}"));
        String hash = jdbc.queryForObject("select otp_hash from email_change_challenges where account_id=?", String.class, patient);
        assertNotEquals(code, hash); assertTrue(encoder.matches(code, hash));
        verifyCode(code).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(target));
        assertEquals(target, accounts.findById(patient).orElseThrow().getNormalizedEmail());
        assertNotNull(accounts.findById(patient).orElseThrow().getEmailVerifiedAt());
        assertEquals(1, count("select count(*) from email_change_challenges where account_id=? and consumed_at is not null", patient));
        verifyCode(code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-82"));
    }

    @ParameterizedTest @ValueSource(strings = {"original", "reused", "unverified", "mismatch"})
    void googleLoginAfterOtpEmailChangePreservesOriginalIdentity(String mode) throws Exception {
        String originalEmail = accounts.findById(patient).orElseThrow().getNormalizedEmail();
        String subject = "kan29-" + UUID.randomUUID();
        when(google.exchange(any(), any(), any()))
                .thenReturn(new GoogleOidcService.VerifiedGoogleIdentity(subject, originalEmail, true));
        var first = googleAuthentication.exchange("code", "verifier", "redirect", "127.0.0.1", "kan29");
        assertEquals(patient, first.body().accountId());
        token = first.body().accessToken();
        var originalLink = identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, subject).orElseThrow();
        String target = email();
        requestEmail(target).andExpect(status().isAccepted());
        verifyCode(codes.get(target)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(target));
        UUID other = null;
        if (mode.equals("reused")) {
            other = account(AccountType.PATIENT, false);
            jdbc.update("update accounts set normalized_email=? where id=?", originalEmail, other);
        }
        int sessionCount = authSessions.findAllByAccountId(patient).size();
        when(google.exchange(any(), any(), any())).thenReturn(new GoogleOidcService.VerifiedGoogleIdentity(
                subject, mode.equals("mismatch") ? target : originalEmail, !mode.equals("unverified")));
        if (mode.equals("unverified") || mode.equals("mismatch")) {
            var ex = assertThrows(GoogleAuthenticationException.class,
                    () -> googleAuthentication.exchange("code", "verifier", "redirect", "127.0.0.1", "kan29"));
            assertEquals(mode.equals("unverified") ? "AUTHENTICATION_FAILED" : "IDENTITY_LINK_CONFLICT", ex.getMessage());
            assertEquals(sessionCount, authSessions.findAllByAccountId(patient).size());
        } else {
            var second = googleAuthentication.exchange("code", "verifier", "redirect", "127.0.0.1", "kan29");
            assertEquals(patient, second.body().accountId());
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + second.body().accessToken()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(patient.toString()))
                    .andExpect(jsonPath("$.email").value(target));
            assertEquals(sessionCount + 1, authSessions.findAllByAccountId(patient).size());
        }
        var link = identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, subject).orElseThrow();
        assertEquals(originalLink.getId(), link.getId());
        assertEquals(originalLink.getLinkedAt(), link.getLinkedAt());
        assertEquals(originalEmail, link.getNormalizedEmailAtLink());
        assertEquals(patient, link.getAccount().getId());
        assertEquals(1, identities.findAllByAccount_Id(patient).size());
        assertEquals(target, accounts.findById(patient).orElseThrow().getNormalizedEmail());
        if (other != null) {
            assertEquals(other, accounts.findByNormalizedEmail(originalEmail).orElseThrow().getId());
            assertTrue(identities.findAllByAccount_Id(other).isEmpty());
            assertEquals(0, authSessions.findAllByAccountId(other).size());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"taken", "current", "rate", "delivery"})
    void latestIntentAlwaysInvalidatesPriorChallengeWithoutEnumeration(String mode) throws Exception {
        String first = email(); requestEmail(first).andExpect(status().isAccepted());
        String oldCode = codes.get(first);
        String next = mode.equals("current") ? accounts.findById(patient).orElseThrow().getNormalizedEmail() : email();
        if (mode.equals("taken")) { var a = accounts.findById(account(AccountType.PATIENT, false)).orElseThrow(); a.setNormalizedEmail(next); accounts.saveAndFlush(a); }
        if (mode.equals("rate")) { requestEmail(email()); requestEmail(email()); }
        if (mode.equals("delivery")) doThrow(new IllegalStateException("delivery unavailable")).when(delivery).sendVerificationCode(eq(next), anyString(), eq(patient));
        String body = requestEmail(next).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals("{\"message\":\"MSG-06\"}", body);
        assertFalse(body.contains(next));
        assertEquals(0, count("select count(*) from email_change_challenges where account_id=? and consumed_at is null and superseded_at is null", patient));
        verifyCode(oldCode).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-82"));
    }

    @Test void resendSupersedesPriorOtp() throws Exception {
        String target = email(); requestEmail(target);
        String first = codes.get(target);
        requestEmail(target).andExpect(status().isAccepted());
        String second = codes.get(target);
        assertEquals(1, count("select count(*) from email_change_challenges where account_id=? and superseded_at is not null", patient));
        // Secure random can legitimately repeat a six-digit value; persistence supersession is authoritative.
        if (!first.equals(second)) verifyCode(first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-82"));
        verifyCode(second).andExpect(status().isOk());
    }

    @Test void wrongOtpPersistsAttemptsAndExhaustion() throws Exception {
        String target = email(); requestEmail(target);
        String wrong = codes.get(target).equals("000000") ? "999999" : "000000";
        for (int i=0;i<5;i++) verifyCode(wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-82"));
        assertEquals(5, count("select failed_attempts from email_change_challenges where account_id=?", patient));
        verifyCode(codes.get(target)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-82"));
    }

    @Test void expiredOrAbsentOtp() throws Exception {
        verifyCode("123456").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-82"));
        String target = email(); requestEmail(target);
        jdbc.update("update email_change_challenges set created_at=now()-interval '20 minutes',expires_at=now()-interval '1 second' where account_id=?", patient);
        verifyCode(codes.get(target)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-82"));
    }

    @Test void emailClaimedAfterRequestIsUnavailable() throws Exception {
        String target = email(); requestEmail(target);
        jdbc.update("update accounts set normalized_email=? where id=?", target, account(AccountType.PATIENT, false));
        verifyCode(codes.get(target)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-08"));
    }

    @Test void competingVerificationsCannotClaimSameEmail() throws Exception {
        String target = email(); requestEmail(target); String firstCode = codes.get(target);
        UUID other = account(AccountType.PATIENT, false);
        var otherActor = new AuthenticatedAccount(other, AccountRole.PATIENT, null);
        emails.request(otherActor, target); String secondCode = codes.get(target);
        var firstActor = new AuthenticatedAccount(patient, AccountRole.PATIENT, null);
        List<Boolean> outcomes = race(() -> attempt(() -> emails.verify(firstActor, firstCode)),
                () -> attempt(() -> emails.verify(otherActor, secondCode)));
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, count("select count(*) from accounts where normalized_email=?", target));
        assertEquals(1, count("select count(*) from email_change_challenges where normalized_target_email=? and consumed_at is not null", target));
    }

    @Test void sameChallengeCanOnlyBeConsumedOnceConcurrently() throws Exception {
        String target = email(); requestEmail(target); String code = codes.get(target);
        var actor = new AuthenticatedAccount(patient, AccountRole.PATIENT, null);
        var outcomes = race(() -> attempt(() -> emails.verify(actor, code)), () -> attempt(() -> emails.verify(actor, code)));
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
    }

    @Test void nonCooperatingEmailClaimRaceCannotCreateDuplicate() throws Exception {
        String target=email(); requestEmail(target); String code=codes.get(target);
        UUID other=account(AccountType.PATIENT,false);
        var outcomes=race(() -> verifyCode(code).andReturn().getResponse().getStatus(), () -> {
            try { jdbc.update("update accounts set normalized_email=? where id=?",target,other); return 200; }
            catch(org.springframework.dao.DataIntegrityViolationException expected) { return 400; }
        });
        assertEquals(1,outcomes.stream().filter(s -> s==200).count());
        assertTrue(outcomes.stream().allMatch(s -> s==200 || s==400));
        assertEquals(1,count("select count(*) from accounts where normalized_email=?",target));
    }

    @Test void profileOwnUpdatePreservesLinkageAndVersion() throws Exception {
        UUID own = profile(patient), other = account(AccountType.PATIENT, false), otherProfile = profile(other);
        mvc.perform(get("/api/v1/me/patient-profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patientProfileId").value(own.toString()))
                .andExpect(jsonPath("$.hasAccount").value(true)).andExpect(jsonPath("$.version").value(0));
        String body = profileBody(0, "  Updated   Person  ");
        mvc.perform(put("/api/v1/me/patient-profile").with(csrf()).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Person")).andExpect(jsonPath("$.version").value(1));
        assertEquals(patient, jdbc.queryForObject("select account_id from patient_profiles where id=?", UUID.class, own));
        assertEquals("Profile name", jdbc.queryForObject("select full_name from patient_profiles where id=?", String.class, otherProfile));
        mvc.perform(put("/api/v1/me/patient-profile").with(csrf()).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
        mvc.perform(get("/api/v1/me/patient-profile/" + otherProfile).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }

    @Test void invalidProfileAndMissingOwnProfileAreSafe() throws Exception {
        mvc.perform(get("/api/v1/me/patient-profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-14"));
        profile(patient);
        String correlation = UUID.randomUUID().toString();
        mvc.perform(put("/api/v1/me/patient-profile").with(csrf()).header("Authorization", "Bearer " + token)
                .header("X-Correlation-ID", correlation).contentType("application/json").content(profileBody(0, "X")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MSG-08"))
                .andExpect(jsonPath("$.error.correlationId").value(correlation));
    }

    @Test void concurrentProfileUpdatesCannotLoseAnUpdate() throws Exception {
        profile(patient);
        Callable<Integer> write=() -> mvc.perform(put("/api/v1/me/patient-profile").with(csrf())
                .header("Authorization","Bearer "+token).contentType("application/json").content(profileBody(0,"Updated Person")))
                .andReturn().getResponse().getStatus();
        var outcomes=race(write,write);
        assertEquals(1,outcomes.stream().filter(s -> s==200).count());
        assertEquals(1,outcomes.stream().filter(s -> s==409).count());
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = "PATIENT", mode = EnumSource.Mode.EXCLUDE)
    void patientOnlyRoutesRejectOtherRoles(AccountType type) throws Exception {
        String other = token(account(type, false));
        for (var r : List.of(get("/api/v1/me/patient-profile"), put("/api/v1/me/patient-profile").content(profileBody(0,"Valid Name")),
                post("/api/v1/me/email-change").content("{\"newEmail\":\"new@example.test\"}"),
                post("/api/v1/me/email-change/verify").content("{\"code\":\"123456\"}")))
            mvc.perform(r.contentType("application/json").with(csrf()).header("Authorization", "Bearer " + other))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MSG-14"));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("changeEmail"))));
    }

    @Test void anonymousAndCsrfDenied() throws Exception {
        for (var r : List.of(get("/api/v1/me"), get("/api/v1/accounts/"+patient+"/avatar"), get("/api/v1/me/patient-profile"),
                patch("/api/v1/me"), delete("/api/v1/me/avatar"), put("/api/v1/me/avatar"), post("/api/v1/me/email-change"),
                post("/api/v1/me/email-change/verify"), put("/api/v1/me/patient-profile")))
            mvc.perform(r.with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/me").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"displayName\":\"Name\"}")).andExpect(status().isForbidden());
    }

    UUID account(AccountType type, boolean forced) {
        var a = new AccountJpaEntity();
        a.setNormalizedEmail(email()); a.setAccountType(type); a.setStatus(AccountStatus.ACTIVE);
        a.setUsername("user-" + UUID.randomUUID()); a.setFullName("Legacy name"); a.setMustChangePassword(forced);
        a.setPasswordHash(encoder.encode("ValidPassword123")); a.setCreatedAt(Instant.now()); a.setUpdatedAt(Instant.now());
        a = accounts.saveAndFlush(a); a.setUsername("user-" + a.getId()); accounts.saveAndFlush(a); return a.getId();
    }
    String token(UUID id) { return sessions.create(id,"127.0.0.1","kan29").body().accessToken(); }
    String email() { return UUID.randomUUID() + "@example.test"; }
    UUID profile(UUID id) {
        UUID p = UUID.randomUUID();
        jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) values (?,?,'Profile name','+84912345678','PATIENT','2000-01-02','OTHER')",p,id);
        return p;
    }
    String profileBody(int version, String name) {
        return json.writeValueAsString(Map.of("fullName",name,"phone","0912345678","phoneOwnerType","PATIENT", "dateOfBirth","2000-01-02",
                "gender","OTHER","address"," Hanoi ","nationalId","12345","identityDocumentType","CCCD","version",version));
    }
    byte[] png(int size) { byte[] result = new byte[size]; byte[] sig = {(byte)137,80,78,71,13,10,26,10}; System.arraycopy(sig,0,result,0,Math.min(size,8)); return result; }
    ResultActions upload(byte[] bytes, String type) throws Exception {
        return mvc.perform(multipart("/api/v1/me/avatar").file(new MockMultipartFile("photo","avatar",type,bytes))
                .with(r -> { r.setMethod("PUT"); return r; }).with(csrf()).header("Authorization","Bearer "+token));
    }
    ResultActions requestEmail(String email) throws Exception {
        return mvc.perform(post("/api/v1/me/email-change").with(csrf()).header("Authorization","Bearer "+token)
                .contentType("application/json").content(json.writeValueAsString(Map.of("newEmail",email))));
    }
    ResultActions verifyCode(String code) throws Exception {
        return mvc.perform(post("/api/v1/me/email-change/verify").with(csrf()).header("Authorization","Bearer "+token)
                .contentType("application/json").content(json.writeValueAsString(Map.of("code",code))));
    }
    int count(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }
    boolean attempt(Runnable task) { try { task.run(); return true; } catch (fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException expected) { return false; } }
    <T> List<T> race(Callable<T> one,Callable<T> two) throws Exception {
        try (var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1); var ready=new CountDownLatch(2);
            var a=pool.submit(() -> { ready.countDown(); start.await(); return one.call(); });
            var b=pool.submit(() -> { ready.countDown(); start.await(); return two.call(); });
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
    }
}
