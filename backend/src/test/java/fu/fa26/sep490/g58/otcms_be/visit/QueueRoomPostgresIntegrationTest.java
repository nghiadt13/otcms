package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.QueueStateMachine;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
class QueueRoomPostgresIntegrationTest {
    private static final String SCHEMA = "p04_queue_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void isolatedSchema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired QueueStateMachine machine;
    @Autowired MockMvc mvc;

    @AfterAll
    static void dropSchema(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    }

    @Test
    void concurrentCallsAndReturnAreSerializedAndAudited() throws Exception {
        UUID receptionist = account("RECEPTIONIST"), doctorAccount = account("DOCTOR"), patientAccount = account("PATIENT");
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), room = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Queue Patient', ?, 'PATIENT', '1990-01-01', 'FEMALE')",
                patient, patientAccount, "+849" + patient.toString().replace("-", "").substring(0, 9));
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, photo_url, work_contact, specialties, qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Queue Doctor', '', '', '', '', '', '', true, true, true)", doctor, doctorAccount);
        jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, ?, 'Queue Room', true)", room, "Q-" + room.toString().substring(0, 8));
        UUID first = entry(patient, receptionist, doctor, room, 1), second = entry(patient, receptionist, doctor, room, 2);
        AuthenticatedAccount actor = new AuthenticatedAccount(receptionist, AccountRole.RECEPTIONIST, UUID.randomUUID());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var calls = List.of(executor.submit(() -> call(start, room, actor)), executor.submit(() -> call(start, room, actor)));
            start.countDown();
            List<String> results = List.of(calls.get(0).get(30, TimeUnit.SECONDS), calls.get(1).get(30, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("CALLED"::equals).count());
            assertEquals(1, results.stream().filter("MSG-42"::equals).count());
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from queue_entries where room_id = ? and status = 'CALLED'", Integer.class, room));
        UUID called = jdbc.queryForObject("select id from queue_entries where room_id = ? and status = 'CALLED'", UUID.class, room);
        UUID waiting = called.equals(first) ? second : first;
        mvc.perform(post("/api/v1/queue/entries/{id}/return", waiting).with(csrf())
                .with(authentication(new UsernamePasswordAuthenticationToken(actor, null,
                        List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST")))))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MSG-42"));
        machine.markAbsent(called, actor);
        machine.returnFromAbsence(called, waiting, "Patient returned", actor);
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where record_type = 'QUEUE_ENTRY' and entity_id = ? and action = 'QUEUE_REPOSITION' and correction_reason = 'Patient returned'", Integer.class, called));
    }

    private String call(CountDownLatch start, UUID room, AuthenticatedAccount actor) throws InterruptedException {
        start.await();
        try { machine.callNext(room, "EXAM", actor); return "CALLED"; }
        catch (VisitOperationException failure) { return failure.code(); }
    }

    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }

    private UUID entry(UUID patient, UUID receptionist, UUID doctor, UUID room, int number) {
        UUID visit = UUID.randomUUID(), entry = UUID.randomUUID();
        Instant at = Instant.now().plusMillis(number);
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)", visit, patient, java.sql.Timestamp.from(at), java.sql.Timestamp.from(at), receptionist);
        jdbc.update("insert into queue_entries (id, visit_id, stage, status, entered_at, doctor_profile_id, room_id, clinic_date, queue_number, queued_at) values (?, ?, 'EXAM', 'WAITING', ?, ?, ?, ?, ?, ?)", entry, visit, java.sql.Timestamp.from(at), doctor, room, LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")), number, java.sql.Timestamp.from(at));
        return entry;
    }
}
