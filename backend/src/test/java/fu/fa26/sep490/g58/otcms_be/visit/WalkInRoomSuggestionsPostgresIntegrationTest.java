package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInRoomSuggestionService;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@LocalPostgresTest
class WalkInRoomSuggestionsPostgresIntegrationTest {
    private static final String SCHEMA = "p05_suggest_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void schema(DynamicPropertyRegistry properties) {
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "set search_path to " + SCHEMA + ", public");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired WalkInRoomSuggestionService suggestions;
    @AfterAll static void dropSchema(@Autowired JdbcTemplate jdbc) { jdbc.execute("drop schema if exists " + SCHEMA + " cascade"); }

    @Test void onDutyRoomsAreOrderedByWaitingCount() {
        UUID receptionist = account("RECEPTIONIST"), patientAccount = account("PATIENT"), patient = UUID.randomUUID();
        UUID busyDoctor = doctor(), freeDoctor = doctor(), busyRoom = room("A"), freeRoom = room("Z");
        Instant now = Instant.now();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) values (?, ?, 'Suggestion Patient', ?, 'PATIENT', '1990-01-01', 'FEMALE')", patient, patientAccount, "+849" + patient.toString().replace("-", "").substring(0, 9));
        for (UUID[] pair : new UUID[][]{{busyDoctor, busyRoom}, {freeDoctor, freeRoom}}) jdbc.update("insert into work_shifts (id, staff_profile_id, starts_at, ends_at, room_id) values (?, ?, ?, ?, ?)", UUID.randomUUID(), pair[0], Timestamp.from(now.minusSeconds(600)), Timestamp.from(now.plusSeconds(3600)), pair[1]);
        for (int number = 1; number <= 2; number++) {
            UUID visit = UUID.randomUUID();
            jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id) values (?, ?, 'WALK_IN', ?, ?, ?)", visit, patient, Timestamp.from(now), Timestamp.from(now), receptionist);
            jdbc.update("insert into queue_entries (visit_id, stage, status, entered_at, doctor_profile_id, room_id, clinic_date, queue_number, queued_at) values (?, 'EXAM', 'WAITING', ?, ?, ?, ?, ?, ?)", visit, Timestamp.from(now), busyDoctor, busyRoom, now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(), number, Timestamp.from(now));
        }
        var actor = new AuthenticatedAccount(receptionist, AccountRole.RECEPTIONIST, UUID.randomUUID());
        var items = suggestions.suggest(actor).items();
        assertEquals(2, items.size());
        assertEquals(freeRoom, items.get(0).roomId());
        assertEquals(0, items.get(0).waitingCount());
        assertEquals(busyRoom, items.get(1).roomId());
        assertEquals(2, items.get(1).waitingCount());
    }
    private UUID doctor() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, publicly_visible, accepting_new_bookings, in_active_directory) values (true, ?, ?, 'Suggestion Doctor', true, true, true)", id, account("DOCTOR"));
        return id;
    }
    private UUID room(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into rooms (id, room_code, display_label, active) values (?, ?, 'Suggestion Room', true)", id, prefix + "-" + id.toString().substring(0, 8));
        return id;
    }
    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())", id, id + "@example.test", role);
        return id;
    }
}
