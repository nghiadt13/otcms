package fu.fa26.sep490.g58.otcms_be.notification;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.notification.service.impl.AppointmentReminderJob;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** NF-09 against real Postgres: window, follow-up wording, overdue notice, reschedule, and no duplicate sends. */
@SpringBootTest @LocalPostgresTest @Transactional
@Import(AppointmentReminderJobPostgresTest.FixedClock.class)
class AppointmentReminderJobPostgresTest {
    static final Instant NOW = Instant.parse("2026-11-18T01:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired AppointmentReminderJob job;
    final UUID patientAccount = UUID.randomUUID(), doctorAccount = UUID.randomUUID(), staffAccount = UUID.randomUUID();
    final UUID profile = UUID.randomUUID(), orphanProfile = UUID.randomUUID(), doctor = UUID.randomUUID();
    final UUID service = UUID.randomUUID(), sourceVisit = UUID.randomUUID();

    @BeforeEach void fixtures() {
        account(patientAccount, "PATIENT"); account(doctorAccount, "DOCTOR"); account(staffAccount, "RECEPTIONIST");
        profile(profile, patientAccount, "Nguyễn Văn An"); profile(orphanProfile, null, "Không có tài khoản");
        jdbc.update("insert into clinic_services(id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?, 'Reminder test service',30,0,true,true)", service);
        jdbc.update("insert into staff_profiles(is_doctor,id,account_id,display_name,publicly_visible,accepting_new_bookings,in_active_directory) values (true,?,?,'Reminder doctor',true,true,true)", doctor, doctorAccount);
        jdbc.update("insert into doctor_services(doctor_profile_id,service_id,active) values (?,?,true)", doctor, service);
        jdbc.update("insert into visits(id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',?,?,?,'CLOSED')",
                sourceVisit, profile, Timestamp.from(NOW.minusSeconds(86400 * 3)), Timestamp.from(NOW.minusSeconds(86400 * 3)), staffAccount);
    }

    @Test void remindsOnceInsideTheWindowAndSecondRunSendsNothing() {
        UUID inside = appointment(profile, "CONFIRMED", "PATIENT_ONLINE", NOW.plusSeconds(20 * 3600));
        appointment(profile, "CONFIRMED", "PATIENT_ONLINE", NOW.plusSeconds(30 * 3600));   // beyond 24h
        appointment(profile, "CONFIRMED", "PATIENT_ONLINE", NOW.minusSeconds(3600));       // already started
        appointment(profile, "CANCELLED", "PATIENT_ONLINE", NOW.plusSeconds(21 * 3600));   // not booked any more

        var first = job.runOnce();
        assertEquals(1, first.reminders());
        var second = job.runOnce();
        assertEquals(0, second.reminders());
        assertEquals(1, second.skipped());

        var rows = jdbc.queryForList("select type, content, related_kind, related_id, recipient_account_id from notifications where type like '%REMINDER%'");
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.getFirst();
        assertEquals("APPOINTMENT_REMINDER", row.get("type"));
        assertEquals("APPOINTMENT", row.get("related_kind"));
        assertEquals(inside, row.get("related_id"));
        assertEquals(patientAccount, row.get("recipient_account_id"));
        // 2026-11-18T21:00Z is 04:00 on 19/11 in Vietnam; the wording follows MSG-80.
        assertEquals("Nhắc lịch khám: Nguyễn Văn An có lịch khám lần đầu vào 04:00 ngày 19/11/2026.", row.get("content"));
    }

    @Test void followUpUsesFollowUpWordingAndOwnType() {
        followUp("CONFIRMED", NOW.plusSeconds(10 * 3600));
        assertEquals(1, job.runOnce().reminders());
        var row = jdbc.queryForMap("select type, content from notifications where type = 'FOLLOW_UP_REMINDER'");
        assertTrue(row.get("content").toString().contains("có lịch tái khám vào 18:00 ngày 18/11/2026"));
    }

    @Test void rescheduledAppointmentIsRemindedAgainAtItsNewTimeButNotTwiceForTheSameTime() {
        UUID id = appointment(profile, "CONFIRMED", "PATIENT_ONLINE", NOW.plusSeconds(5 * 3600));
        assertEquals(1, job.runOnce().reminders());
        jdbc.update("update appointments set start_time=?, end_time=?, occupied_until=? where id=?",
                Timestamp.from(NOW.plusSeconds(8 * 3600)), Timestamp.from(NOW.plusSeconds(8 * 3600 + 1800)),
                Timestamp.from(NOW.plusSeconds(8 * 3600 + 1800)), id);
        assertEquals(1, job.runOnce().reminders());
        assertEquals(0, job.runOnce().reminders());
        assertEquals(2, count("type = 'APPOINTMENT_REMINDER' and related_id = '" + id + "'"));
    }

    @Test void overdueFollowUpIsAnnouncedOnceAndOnlyForRecentFollowUps() {
        UUID overdue = followUp("NO_SHOW", NOW.minusSeconds(2 * 86400));
        followUp("NO_SHOW", NOW.minusSeconds(40L * 86400));                                  // beyond the 30-day lookback
        appointment(profile, "NO_SHOW", "PATIENT_ONLINE", NOW.minusSeconds(2 * 86400 + 7200)); // first visit, not a follow-up
        followUp("CONFIRMED", NOW.minusSeconds(3600));                                         // not yet marked NO_SHOW

        var first = job.runOnce();
        assertEquals(1, first.overdue());
        assertEquals(0, first.reminders());
        assertEquals(0, job.runOnce().overdue());
        var row = jdbc.queryForMap("select content, related_id from notifications where type = 'FOLLOW_UP_OVERDUE'");
        assertEquals(overdue, row.get("related_id"));
        assertEquals("Nguyễn Văn An chưa quay lại theo hẹn ngày 16/11/2026, vui lòng liên hệ phòng khám để đặt lịch.", row.get("content"));
    }

    @Test void patientWithoutAccountIsSkippedWithoutFailingTheBatch() {
        appointment(orphanProfile, "CONFIRMED", "PATIENT_ONLINE", NOW.plusSeconds(3 * 3600));
        UUID ok = appointment(profile, "CONFIRMED", "PATIENT_ONLINE", NOW.plusSeconds(6 * 3600));
        var result = job.runOnce();
        assertEquals(1, result.reminders());
        assertEquals(1, result.skipped());
        assertEquals(1, count("related_id = '" + ok + "'"));
    }

    @Test void databaseRefusesDuplicateDedupeKeysButNotMissingOnes() {
        insertRaw(null); insertRaw(null);
        insertRaw("k:1");
        assertThrows(DuplicateKeyException.class, () -> insertRaw("k:1"));
    }

    private void insertRaw(String key) {
        jdbc.update("insert into notifications(id,recipient_account_id,type,title,content,created_at,dedupe_key) values (?,?,'T','t','c',now(),?)",
                UUID.randomUUID(), patientAccount, key);
    }
    private int count(String where) {
        return jdbc.queryForObject("select count(*) from notifications where " + where, Integer.class);
    }
    private UUID followUp(String status, Instant start) {
        return appointment(profile, status, "DOCTOR_FOLLOW_UP", start);
    }
    private UUID appointment(UUID patient, String status, String source, Instant start) {
        UUID id = UUID.randomUUID();
        boolean followUp = source.equals("DOCTOR_FOLLOW_UP");
        jdbc.update("insert into appointments(id,patient_profile_id,service_id,doctor_profile_id,status,booking_source,purpose,start_time,end_time,"
                        + "duration_minutes,buffer_minutes,occupied_until,created_by_account_id,source_visit_id,ordering_doctor_profile_id) "
                        + "values (?,?,?,?,?,?,'Reminder test',?,?,30,0,?,?,?,?)",
                id, patient, service, doctor, status, source, Timestamp.from(start), Timestamp.from(start.plusSeconds(1800)),
                Timestamp.from(start.plusSeconds(1800)), staffAccount, followUp ? sourceVisit : null, followUp ? doctor : null);
        return id;
    }
    private void account(UUID id, String role) {
        jdbc.update("insert into accounts(id, normalized_email, account_type, status, password_hash, created_at, updated_at) values (?,?,?,'ACTIVE','test',now(),now())",
                id, "reminder-" + id + "@example.test", role);
    }
    private void profile(UUID id, UUID account, String name) {
        jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) "
                + "values (?,?,?,?,'PATIENT',date '1990-01-01','OTHER')", id, account, name,
                "+849" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L)));
    }

    @TestConfiguration static class FixedClock {
        @Bean @Primary Clock reminderClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
