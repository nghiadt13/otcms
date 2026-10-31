package fu.fa26.sep490.g58.otcms_be.billing;

import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CounterPaymentRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CreateVisitInvoiceRequest;
import fu.fa26.sep490.g58.otcms_be.billing.service.InvoiceService;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
@LocalPostgresTest
class BillingInvoicePostgresIntegrationTest {
    private static final BigDecimal SERVICE_PRICE = new BigDecimal("125000.00");

    @Autowired JdbcTemplate jdbc;
    @Autowired InvoiceService invoices;
    @Autowired PaymentService payments;

    private final UUID patientAccountId = UUID.randomUUID();
    private final UUID receptionistAccountId = UUID.randomUUID();
    private final UUID doctorAccountId = UUID.randomUUID();
    private final UUID patientProfileId = UUID.randomUUID();
    private final UUID doctorProfileId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final UUID appointmentId = UUID.randomUUID();
    private final AuthenticatedAccount receptionist = new AuthenticatedAccount(
            receptionistAccountId, AccountRole.RECEPTIONIST, UUID.randomUUID());

    @BeforeEach
    void createBillingSources() {
        removeStaleFixturesFromEarlierRun();
        Instant now = Instant.now();
        insertAccount(patientAccountId, "PATIENT");
        insertAccount(receptionistAccountId, "RECEPTIONIST");
        insertAccount(doctorAccountId, "DOCTOR");

        jdbc.update("insert into patient_profiles (id, account_id, full_name, phone_canonical, "
                        + "phone_owner_type, date_of_birth, gender) values (?, ?, ?, ?, 'PATIENT', date '1990-01-01', 'OTHER')",
                patientProfileId, patientAccountId, "Billing Test Patient",
                "+849" + String.format("%09d", Math.floorMod(patientProfileId.getLeastSignificantBits(), 1_000_000_000L)));
        jdbc.update("insert into doctor_profiles (id, account_id, full_name) values (?, ?, ?)",
                doctorProfileId, doctorAccountId, "Billing Test Doctor");
        jdbc.update("insert into clinic_services (id, name, service_type, price, duration_minutes, "
                        + "buffer_minutes, online_bookable, active) values (?, ?, 'EXAMINATION', ?, 30, 0, true, true)",
                serviceId, "Billing Test Examination", SERVICE_PRICE);
        jdbc.update("insert into doctor_services (doctor_profile_id, service_id, active) values (?, ?, true)",
                doctorProfileId, serviceId);

        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, "
                        + "created_by_account_id, status) values (?, ?, 'WALK_IN', ?, ?, ?, 'CLOSED')",
                visitId, patientProfileId, Timestamp.from(now), Timestamp.from(now), receptionistAccountId);

        Instant appointmentStart = now.plusSeconds(7 * 24 * 60 * 60L);
        jdbc.update("insert into appointments (id, patient_profile_id, service_id, doctor_profile_id, status, "
                        + "booking_source, purpose, start_time, end_time, duration_minutes, buffer_minutes, "
                        + "created_by_account_id) values (?, ?, ?, ?, 'CONFIRMED', 'PATIENT_ONLINE', ?, ?, ?, 30, 0, ?)",
                appointmentId, patientProfileId, serviceId, doctorProfileId, "Billing integration test",
                Timestamp.from(appointmentStart), Timestamp.from(appointmentStart.plusSeconds(30 * 60L)), patientAccountId);
    }

    @Test
    void migratesBillingSchemaAndCreatesOneCorrectInvoicePerSourceWithPartialCounterPayment() {
        assertEquals(3, jdbc.queryForObject("select count(*) from flyway_schema_history "
                + "where version in ('042', '043', '044') and success", Integer.class));

        var visitRequest = new CreateVisitInvoiceRequest(List.of(
                new CreateVisitInvoiceRequest.Item(serviceId, 2)));
        var visitInvoice = invoices.createForVisit(visitId, visitRequest, receptionist);
        var duplicateVisitInvoice = invoices.createForVisit(visitId,
                new CreateVisitInvoiceRequest(List.of(new CreateVisitInvoiceRequest.Item(serviceId, 1))), receptionist);

        assertEquals(visitInvoice.id(), duplicateVisitInvoice.id());
        assertEquals(1, jdbc.queryForObject("select count(*) from invoices where visit_id = ?", Integer.class, visitId));
        assertEquals(new BigDecimal("250000.00"), visitInvoice.totalAmount());
        assertEquals(1, visitInvoice.items().size());
        assertEquals(SERVICE_PRICE, visitInvoice.items().get(0).unitPrice());
        assertEquals(new BigDecimal("250000.00"), visitInvoice.items().get(0).lineTotal());

        var appointmentInvoice = invoices.createForAppointment(appointmentId, receptionist);
        var duplicateAppointmentInvoice = invoices.createForAppointment(appointmentId, receptionist);

        assertEquals(appointmentInvoice.id(), duplicateAppointmentInvoice.id());
        assertEquals(1, jdbc.queryForObject("select count(*) from invoices where appointment_id = ?",
                Integer.class, appointmentId));
        assertNotEquals(visitInvoice.id(), appointmentInvoice.id());
        assertEquals(new BigDecimal("125000.00"), appointmentInvoice.totalAmount());
        assertEquals(SERVICE_PRICE, appointmentInvoice.items().get(0).unitPrice());

        var payment = payments.recordCounterPayment(visitInvoice.id(),
                new CounterPaymentRequest(100_000L, "CASH"), receptionist, "billing-test-" + UUID.randomUUID());
        var updatedVisitInvoice = invoices.get(visitInvoice.id(), receptionist);

        assertEquals("CONFIRMED", payment.status());
        assertEquals(new BigDecimal("100000.00"), payment.amount());
        assertEquals(new BigDecimal("100000.00"), updatedVisitInvoice.paidAmount());
        assertEquals(new BigDecimal("150000.00"), updatedVisitInvoice.outstandingBalance());
        assertEquals("OPEN", updatedVisitInvoice.status());
    }

    @AfterEach
    void removeOnlyFixturesCreatedByThisTest() {
        jdbc.update("delete from payment_attempts where invoice_id in (select id from invoices where visit_id = ? or appointment_id = ?)",
                visitId, appointmentId);
        jdbc.update("delete from invoice_items where invoice_id in (select id from invoices where visit_id = ? or appointment_id = ?)",
                visitId, appointmentId);
        jdbc.update("delete from invoices where visit_id = ? or appointment_id = ?", visitId, appointmentId);
        jdbc.update("delete from appointments where id = ?", appointmentId);
        jdbc.update("delete from visits where id = ?", visitId);
        jdbc.update("delete from doctor_services where doctor_profile_id = ? and service_id = ?",
                doctorProfileId, serviceId);
        jdbc.update("delete from clinic_services where id = ?", serviceId);
        jdbc.update("delete from doctor_profiles where id = ?", doctorProfileId);
        jdbc.update("delete from patient_profiles where id = ?", patientProfileId);
        jdbc.update("delete from accounts where id in (?, ?, ?)",
                patientAccountId, receptionistAccountId, doctorAccountId);
    }

    private void insertAccount(UUID id, String type) {
        jdbc.update("insert into accounts (id, normalized_email, account_type, status, password_hash, created_at, updated_at) "
                        + "values (?, ?, ?, 'ACTIVE', 'integration-test', now(), now())",
                id, "billing-test-" + id + "@example.test", type);
    }

    private void removeStaleFixturesFromEarlierRun() {
        String testAccounts = "select id from accounts where normalized_email like 'billing-test-%@example.test'";
        jdbc.update("delete from payment_attempts where invoice_id in (select id from invoices "
                + "where created_by_account_id in (" + testAccounts + "))");
        jdbc.update("delete from invoice_items where invoice_id in (select id from invoices "
                + "where created_by_account_id in (" + testAccounts + "))");
        jdbc.update("delete from invoices where created_by_account_id in (" + testAccounts + ")");
        jdbc.update("delete from appointments where created_by_account_id in (" + testAccounts + ")");
        jdbc.update("delete from visits where created_by_account_id in (" + testAccounts + ")");
        jdbc.update("delete from doctor_services where doctor_profile_id in (select d.id from doctor_profiles d "
                + "join accounts a on a.id = d.account_id where a.normalized_email like 'billing-test-%@example.test')");
        jdbc.update("delete from clinic_services where name = 'Billing Test Examination'");
        jdbc.update("delete from doctor_profiles where account_id in (" + testAccounts + ")");
        jdbc.update("delete from patient_profiles where account_id in (" + testAccounts + ")");
        jdbc.update("delete from accounts where normalized_email like 'billing-test-%@example.test'");
    }
}
