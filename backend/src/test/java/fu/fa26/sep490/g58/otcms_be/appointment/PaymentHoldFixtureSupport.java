package fu.fa26.sep490.g58.otcms_be.appointment;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared fixture for the AWAITING_PAYMENT hold integration tests: one doctor with a shift on 2026-10-16
 * (09:00-12:00 clinic time), a paid and a free online-bookable service, two patients and a receptionist, a movable
 * clock. Subclasses choose the billing side: a mocked settlement or the real billing with a fake gateway.
 */
abstract class PaymentHoldFixtureSupport {
    static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    /** 10:00 clinic time inside the doctor's shift. */
    static final Instant SLOT = Instant.parse("2026-10-16T03:00:00Z");
    static final Instant SLOT_2 = SLOT.plusSeconds(1800);
    static final Instant SLOT_3 = SLOT.plusSeconds(3600);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MovableClock clock;

    UUID patientA, patientB, receptionist, doctorAccount;
    UUID profileA, profileB, doctorProfile, paidService, freeService;

    @BeforeEach
    void setUpHoldFixture() {
        clock.set(NOW);
        patientA = account("PATIENT", "a");
        patientB = account("PATIENT", "b");
        receptionist = account("RECEPTIONIST", "rec");
        doctorAccount = account("DOCTOR", "doc");
        profileA = patientProfile(patientA, "Hold Patient A");
        profileB = patientProfile(patientB, "Hold Patient B");
        doctorProfile = UUID.randomUUID();
        paidService = service("Paid visit", 150000);
        freeService = service("Free visit", 0);
        jdbc.update("insert into staff_profiles (is_doctor, id, account_id, display_name, photo_url, work_contact, specialties, "
                + "qualifications, biography, consultation_information, publicly_visible, accepting_new_bookings, "
                + "in_active_directory) values (true, ?, ?, 'Hold Doctor', '', 'private', '', '', '', '', true, true, true)",
                doctorProfile, doctorAccount);
        for (UUID service : List.of(paidService, freeService)) {
            jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                    doctorProfile, service);
        }
        jdbc.update("insert into work_shifts (id, staff_profile_id, starts_at, ends_at) values (?, ?, ?, ?)",
                UUID.randomUUID(), doctorProfile, Timestamp.from(Instant.parse("2026-10-16T02:00:00Z")),
                Timestamp.from(Instant.parse("2026-10-16T05:00:00Z")));
    }

    @AfterEach
    void cleanUpHoldFixture() {
        if (patientA == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            jdbc.execute("alter table audit_logs disable trigger tr_audit_logs_immutable");
            try {
                String creators = "(?, ?, ?)";
                Object[] args = {patientA, patientB, receptionist};
                String mine = "(select id from appointments where created_by_account_id in " + creators + ")";
                String myInvoices = "(select id from invoices where appointment_id in " + mine + ")";
                jdbc.update("delete from payment_attempts where invoice_id in " + myInvoices, args);
                jdbc.update("delete from invoice_items where invoice_id in " + myInvoices, args);
                jdbc.update("delete from invoices where appointment_id in " + mine, args);
                jdbc.update("delete from notifications where related_id in (select id from appointments "
                        + "where created_by_account_id in " + creators + ")", args);
                jdbc.update("delete from audit_logs where source = 'appointment_audits' and entity_id in (select id from appointments "
                        + "where created_by_account_id in " + creators + ")", args);
                jdbc.update("delete from appointments where created_by_account_id in " + creators, args);
                jdbc.update("delete from doctor_services where doctor_profile_id = ?", doctorProfile);
                jdbc.update("delete from clinic_services where id in (?, ?)", paidService, freeService);
                jdbc.update("delete from work_shifts where staff_profile_id = ?", doctorProfile);
                jdbc.update("delete from staff_profiles where id = ?", doctorProfile);
                jdbc.update("delete from patient_profiles where id in (?, ?)", profileA, profileB);
                jdbc.update("delete from accounts where id in (?, ?, ?, ?)", patientA, patientB, receptionist, doctorAccount);
            } finally {
                jdbc.execute("alter table audit_logs enable trigger tr_audit_logs_immutable");
            }
        });
    }

    // ---- HTTP helpers ----------------------------------------------------------------------------------------

    ResultActions createOnline(UUID account, UUID service, Instant start) throws Exception {
        return mvc.perform(post("/api/v1/appointments").with(actor(account, AccountRole.PATIENT)).with(csrf())
                .contentType("application/json").content(createJson(null, service, start)));
    }

    ResultActions createByReceptionist(UUID patientProfile, UUID service, Instant start) throws Exception {
        return mvc.perform(post("/api/v1/appointments").with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                .contentType("application/json").content(createJson(patientProfile, service, start)));
    }

    /** Creates an online hold on the paid service and returns its id. */
    UUID createHold(UUID account, Instant start) throws Exception {
        String body = createOnline(account, paidService, start).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        return UUID.fromString(id);
    }

    String createJson(UUID patientProfile, UUID service, Instant start) {
        StringBuilder json = new StringBuilder("{");
        if (patientProfile != null) json.append("\"patientProfileId\":\"").append(patientProfile).append("\",");
        return json.append("\"serviceId\":\"").append(service).append("\",\"doctorId\":\"").append(doctorProfile)
                .append("\",\"startTime\":\"").append(start).append("\",\"purpose\":\"Hold test\"}").toString();
    }

    static RequestPostProcessor actor(UUID id, AccountRole role) {
        var principal = new AuthenticatedAccount(id, role, UUID.randomUUID());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    /** Reads a JSON path, null when absent or null. */
    static Object json(String body, String path) {
        return JsonPath.using(Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS))
                .parse(body).read(path);
    }

    // ---- database helpers ------------------------------------------------------------------------------------

    String statusOf(UUID appointmentId) {
        return jdbc.queryForObject("select status from appointments where id = ?", String.class, appointmentId);
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    int notificationsFor(UUID appointmentId) {
        return count("select count(*) from notifications where related_id = ?", appointmentId);
    }

    /** Inserts a 30-minute appointment row directly (fixtures that must not go through the booking rules). */
    UUID insertAppointment(UUID creator, UUID profile, UUID service, Instant start, String status, Instant deadline,
            String source) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, "
                + "created_by_account_id, payment_deadline) values (?, ?, ?, ?, ?, ?, 'fixture', ?, ?, 30, 0, ?, ?)",
                id, profile, service, doctorProfile, status, source, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(1800)), creator, deadline == null ? null : Timestamp.from(deadline));
        return id;
    }

    UUID insertHold(UUID creator, UUID profile, Instant start, Instant deadline) {
        return insertAppointment(creator, profile, paidService, start, "AWAITING_PAYMENT", deadline, "PATIENT_ONLINE");
    }

    private UUID account(String type, String suffix) {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, created_at, updated_at) "
                + "values (?, ?, ?, 'ACTIVE', ?, ?)", id, "hold-" + suffix + "-" + id + "@example.test", type,
                Timestamp.from(created), Timestamp.from(created));
        return id;
    }

    private UUID patientProfile(UUID accountId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, phone_owner_type, "
                + "date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', '1990-01-01', 'OTHER')",
                id, accountId, name, "+849" + UUID.randomUUID().toString().replace("-", "").substring(0, 9));
        return id;
    }

    private UUID service(String name, int price) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clinic_services (id, name, duration_minutes, buffer_minutes, online_bookable, active, "
                + "price) values (?, ?, 30, 0, true, true, ?)", id, name, price);
        return id;
    }

    // ---- clock -----------------------------------------------------------------------------------------------

    /** Clock the tests can move (hold deadlines are relative to it). */
    static final class MovableClock extends Clock {
        private volatile Instant now = NOW;

        void set(Instant instant) { this.now = instant; }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Primary @Bean MovableClock paymentHoldTestClock() { return new MovableClock(); }
    }
}
