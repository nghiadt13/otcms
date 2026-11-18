package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * UC34 danh sách lượt: bản thân và người thân đã xác nhận thấy lượt; người lạ không thấy; tài khoản chưa có
 * hồ sơ nhận mảng rỗng; không phải bệnh nhân thì 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyVisitPostgresIntegrationTest {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String SCHEMA = "p04_myvisit_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void isolatedSchema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private Instant seededAt;
    private UUID receptionist, doctor, room, selfAccount, selfProfile, childProfile, strangerAccount, noProfileAccount;
    private UUID inExam, left, childWaiting, closedRecent, openOld, strangerVisit;

    @BeforeAll
    void seed() {
        seededAt = Instant.now();
        receptionist = account("RECEPTIONIST");
        doctor = doctorProfile();
        room = room("My Visit Room 1");
        selfAccount = account("PATIENT");
        selfProfile = profile(selfAccount, "My Visit Self");
        childProfile = profile(null, "My Visit Child");
        confirmedLink(selfAccount, childProfile);
        strangerAccount = account("PATIENT");
        UUID strangerProfile = profile(strangerAccount, "My Visit Stranger");
        noProfileAccount = account("PATIENT");

        openOld = visit(selfProfile, "OPEN", seededAt.minus(30, ChronoUnit.DAYS));
        closedRecent = visit(selfProfile, "CLOSED", seededAt.minus(2, ChronoUnit.DAYS));
        visit(selfProfile, "CLOSED", seededAt.minus(10, ChronoUnit.DAYS));
        UUID pendingPayment = visit(selfProfile, "OPEN", seededAt.minus(5, ChronoUnit.MINUTES));
        queueEntry(pendingPayment, "PENDING_PAYMENT", null, null);
        inExam = visit(selfProfile, "OPEN", seededAt.minus(10, ChronoUnit.MINUTES));
        queueEntry(inExam, "IN_EXAMINATION", 2, room);
        left = visit(selfProfile, "OPEN", seededAt.minus(20, ChronoUnit.MINUTES));
        queueEntry(left, "CANCELLED", null, null);
        childWaiting = visit(childProfile, "OPEN", seededAt.minus(30, ChronoUnit.MINUTES));
        queueEntry(childWaiting, "WAITING", 3, room);
        strangerVisit = visit(strangerProfile, "OPEN", seededAt.minus(1, ChronoUnit.MINUTES));
    }

    @AfterAll
    void dropSchema() {
        jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    }

    @Test
    void ownAndApprovedDependentVisitsAreListedNewestFirstWithQueueAndRoomButNoResults() throws Exception {
        JsonNode list = myVisits(selfAccount, AccountRole.PATIENT);
        assertTrue(list.isArray());
        assertEquals(List.of(inExam, left, childWaiting, closedRecent, openOld), ids(list));
        for (JsonNode visit : list) {
            assertEquals(9, visit.size(), "only the list summary fields are exposed; results never appear (BR-35)");
        }

        JsonNode inExamItem = item(list, inExam);
        assertEquals(selfProfile.toString(), inExamItem.get("patientProfileId").asString());
        assertEquals("My Visit Self", inExamItem.get("patientName").asString());
        assertEquals("SELF", inExamItem.get("relationship").asString());
        assertEquals("IN_EXAMINATION", inExamItem.get("status").asString());
        assertEquals(2, inExamItem.get("queueNumber").asInt());
        assertEquals("My Visit Room 1", inExamItem.get("roomName").asString());

        JsonNode childItem = item(list, childWaiting);
        assertEquals("My Visit Child", childItem.get("patientName").asString());
        assertEquals("CHILD", childItem.get("relationship").asString());
        assertEquals("WAITING_FOR_EXAMINATION", childItem.get("status").asString());
        assertEquals(3, childItem.get("queueNumber").asInt());
        assertEquals("My Visit Room 1", childItem.get("roomName").asString());
        assertEquals(seededAt.minus(30, ChronoUnit.MINUTES).atZone(CLINIC_ZONE).toLocalDate().toString(),
                childItem.get("visitDate").asString());

        JsonNode closed = item(list, closedRecent);
        assertEquals("COMPLETED", closed.get("status").asString());
        assertTrue(closed.get("queueNumber").isNull());
        assertTrue(closed.get("roomName").isNull());
        assertEquals("CANCELLED_LEFT", item(list, left).get("status").asString());
        assertEquals("WAITING_FOR_EXAMINATION", item(list, openOld).get("status").asString());
    }

    @Test
    void strangerSeesOnlyOwnVisits() throws Exception {
        assertEquals(List.of(strangerVisit), ids(myVisits(strangerAccount, AccountRole.PATIENT)));
    }

    @Test
    void accountWithoutProfileGetsEmptyListNotForbidden() throws Exception {
        JsonNode list = myVisits(noProfileAccount, AccountRole.PATIENT);
        assertTrue(list.isArray());
        assertEquals(0, list.size());
    }

    @Test
    void receptionistCannotUseThePatientEndpoint() throws Exception {
        mvc.perform(get("/api/v1/me/visits").with(auth(receptionist, AccountRole.RECEPTIONIST)))
                .andExpect(status().isForbidden());
    }

    private JsonNode myVisits(UUID accountId, AccountRole role) throws Exception {
        String body = mvc.perform(get("/api/v1/me/visits").with(auth(accountId, role)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static RequestPostProcessor auth(UUID accountId, AccountRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccount(accountId, role, UUID.randomUUID()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private static List<UUID> ids(JsonNode list) {
        List<UUID> ids = new ArrayList<>();
        list.forEach(visit -> ids.add(UUID.fromString(visit.get("visitId").asString())));
        return ids;
    }

    private static JsonNode item(JsonNode list, UUID visitId) {
        for (JsonNode visit : list) {
            if (visitId.toString().equals(visit.get("visitId").asString())) return visit;
        }
        throw new AssertionError("visit not listed: " + visitId);
    }

    private UUID account(String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                + "values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", type);
        return id;
    }

    private UUID doctorProfile() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, photo_url, work_contact, "
                + "specialties, qualifications, biography, consultation_information, publicly_visible, "
                + "accepting_new_bookings, in_active_directory) "
                + "values (true, ?, ?, 'My Visit Doctor', '', '', '', '', '', '', true, true, true)",
                id, account("DOCTOR"));
        return id;
    }

    private UUID room(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, ?, ?, true)",
                id, "MV-" + id.toString().substring(0, 8), label);
        return id;
    }

    private UUID profile(UUID accountId, String fullName) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                + "date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')",
                id, accountId, fullName, "+849" + id.toString().replace("-", "").substring(0, 9));
        return id;
    }

    private void confirmedLink(UUID requesterAccount, UUID dependentProfile) {
        jdbc.update("insert into patient_caregivers(id,requester_account_id,dependent_patient_profile_id,"
                + "confirming_account_id,state,requested_at,confirmed_at,relationship,verification_method,"
                + "verified_by_account_id,verified_at) "
                + "values (?,?,?,?,'CONFIRMED',now(),now(),'CHILD','COUNTER_CHECK',?,now())",
                UUID.randomUUID(), requesterAccount, dependentProfile, receptionist, receptionist);
    }

    private UUID visit(UUID patientProfileId, String status, Instant checkedInAt) {
        UUID id = UUID.randomUUID();
        Timestamp at = Timestamp.from(checkedInAt);
        jdbc.update("insert into visits (id, patient_profile_id, source, status, arrived_at, checked_in_at, "
                + "created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?, ?)",
                id, patientProfileId, status, at, at, receptionist);
        return id;
    }

    private void queueEntry(UUID visitId, String status, Integer queueNumber, UUID roomId) {
        jdbc.update("insert into queue_entries (id, visit_id, stage, status, entered_at, doctor_profile_id, "
                + "room_id, clinic_date, queue_number, queued_at) values (?, ?, 'EXAM', ?, now(), ?, ?, ?, ?, now())",
                UUID.randomUUID(), visitId, status, doctor, roomId, LocalDate.now(CLINIC_ZONE), queueNumber);
    }
}
