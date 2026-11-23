package fu.fa26.sep490.g58.otcms_be.billing;

import fu.fa26.sep490.g58.otcms_be.billing.event.ServiceOrderPaidEvent;
import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.model.PaymentChannel;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.CounterPaymentRequest;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.request.PaymentAttemptRequest;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingQueryService;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingRefundService;
import fu.fa26.sep490.g58.otcms_be.billing.service.PaymentGateway;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Manual refund workflow, the paid-service-order query and its event, on real Postgres. */
@RecordApplicationEvents
class BillingRefundPostgresTest extends HoldBillingTestSupport {
    @Autowired BillingRefundService refunds;
    @Autowired BillingQueryService billingQuery;
    @Autowired ApplicationEvents events;

    private final UUID visitId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    // doctorAccountId is a real account row (FK of refunded_by_account_id); only the role makes it an admin here.
    private AuthenticatedAccount admin() { return new AuthenticatedAccount(doctorAccountId, AccountRole.ADMIN, UUID.randomUUID()); }

    private UUID paidFeeItem() {
        var invoice = invoiceFor(appointment("CONFIRMED", null));
        UUID item = invoice.items().getFirst().id();
        payments.recordCounterPayment(invoice.id(), new CounterPaymentRequest(List.of(item), "CASH"), receptionist,
                "refund-" + UUID.randomUUID());
        return item;
    }

    private String refundStatus(UUID itemId) {
        return jdbc.queryForObject("select refund_status from invoice_items where id = ?", String.class, itemId);
    }

    private UUID invoiceOf(UUID itemId) {
        return jdbc.queryForObject("select invoice_id from invoice_items where id = ?", UUID.class, itemId);
    }

    @AfterEach
    void removeOrderFixture() {
        jdbc.update("update invoice_items set service_order_id = null where service_order_id = ?", orderId);
        jdbc.update("delete from service_orders where id = ?", orderId);
        jdbc.update("delete from service_order_rounds where visit_id = ?", visitId);
        jdbc.update("delete from visits where id = ?", visitId);
    }

    private static RequestPostProcessor as(AuthenticatedAccount principal) {
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.accountRole().name()))));
    }

    @Test
    void httpAdminListsAndConfirmsRefundsAndOtherRolesAreRefused() throws Exception {
        UUID item = paidFeeItem();
        refunds.requestRefund(item, "Doctor on leave");
        mockMvc.perform(get("/api/v1/admin/refunds").param("status", "PENDING").param("size", "100").with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.itemId=='" + item + "')].status").value("REFUND_PENDING"))
                .andExpect(jsonPath("$.items[?(@.itemId=='" + item + "')].patientName").value("Hold Billing Patient"));
        mockMvc.perform(get("/api/v1/admin/refunds").with(as(receptionist))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/refunds").param("status", "ALL").with(as(admin()))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MSG-54"));

        mockMvc.perform(post("/api/v1/invoice-items/{id}/refund-confirmation", item).with(as(receptionist)).with(csrf())
                .contentType("application/json").content("{\"note\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/invoice-items/{id}/refund-confirmation", item).with(as(admin())).with(csrf())
                        .contentType("application/json").content("{\"note\":\"Hoàn tiền mặt tại quầy\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.amount").value(125000.0)).andExpect(jsonPath("$.patientName").value("Hold Billing Patient"));
        mockMvc.perform(post("/api/v1/invoice-items/{id}/refund-confirmation", item).with(as(admin())).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("MSG-42"));
    }

    @Test
    void paidItemGoesToRefundPendingThenAnAdminConfirmsItWithAnAuditRow() {
        UUID item = paidFeeItem();
        refunds.requestRefund(item, "Bác sĩ nghỉ");
        refunds.requestRefund(item, "Another reason is ignored"); // idempotent: the first reason stays
        assertEquals("REFUND_PENDING", refundStatus(item));
        assertEquals("Bác sĩ nghỉ", jdbc.queryForObject("select refund_reason from invoice_items where id = ?", String.class, item));
        assertEquals("PAID", invoiceStatus(invoiceOf(item)));

        var pending = refunds.listRefunds(admin(), "PENDING", 0, 100).items().stream()
                .filter(refund -> refund.itemId().equals(item)).findFirst().orElseThrow();
        assertEquals(0, PRICE.compareTo(pending.amount()));
        assertEquals("Hold Billing Patient", pending.patientName());
        assertEquals("REFUND_PENDING", pending.status());

        assertEquals("ACCESS_DENIED", assertThrows(BillingException.class,
                () -> refunds.confirmRefund(item, "cash", receptionist)).code());
        var done = refunds.confirmRefund(item, "Paid back in cash at the desk", admin());
        assertEquals("REFUNDED", done.status());
        assertEquals("Hold Billing Patient", done.patientName());
        assertNotNull(done.refundedAt());
        assertEquals(doctorAccountId, jdbc.queryForObject("select refunded_by_account_id from invoice_items where id = ?", UUID.class, item));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where action = 'REFUND_CONFIRMED' "
                + "and record_type = 'INVOICE_ITEM' and entity_id = ? and actor_account_id = ?", Integer.class, item, doctorAccountId));

        assertEquals("MSG-42", assertThrows(BillingException.class, () -> refunds.confirmRefund(item, null, admin())).code());
        assertFalse(refunds.listRefunds(admin(), "PENDING", 0, 100).items().stream().anyMatch(r -> r.itemId().equals(item)));
        assertTrue(refunds.listRefunds(admin(), "REFUNDED", 0, 100).items().stream().anyMatch(r -> r.itemId().equals(item)));
        assertEquals("MSG-54", assertThrows(BillingException.class, () -> refunds.listRefunds(admin(), "ALL", 0, 20)).code());
        assertEquals("ACCESS_DENIED", assertThrows(BillingException.class, () -> refunds.listRefunds(receptionist, "PENDING", 0, 20)).code());
    }

    @Test
    void confirmingAnItemThatWaitsForNoRefundIsMsg42AndAnUnknownItemIsMsg41() {
        UUID item = paidFeeItem();
        assertEquals("MSG-42", assertThrows(BillingException.class, () -> refunds.confirmRefund(item, null, admin())).code());
        assertEquals("MSG-41", assertThrows(BillingException.class, () -> refunds.confirmRefund(UUID.randomUUID(), null, admin())).code());
        assertEquals("MSG-41", assertThrows(BillingException.class, () -> refunds.requestRefund(UUID.randomUUID(), "x")).code());
    }

    @Test
    void anUnpaidItemIsVoidedAndTheInvoiceSettlesWithoutIt() {
        var invoice = invoiceFor(appointment("CONFIRMED", null));
        UUID fee = invoice.items().getFirst().id();
        UUID extra = UUID.randomUUID();
        jdbc.update("insert into invoice_items (id, invoice_id, line_number, service_id, description_snapshot, quantity, "
                + "unit_price_snapshot) values (?, ?, 2, ?, 'Extra', 1, 50000)", extra, invoice.id(), serviceId);
        payments.recordCounterPayment(invoice.id(), new CounterPaymentRequest(List.of(fee), "CASH"), receptionist, "v-" + UUID.randomUUID());
        assertEquals("OPEN", invoiceStatus(invoice.id()));

        refunds.requestRefund(extra, "Order cancelled before payment");

        assertNull(refundStatus(extra));
        assertNotNull(jdbc.queryForObject("select voided_at from invoice_items where id = ?", Timestamp.class, extra));
        assertEquals("PAID", invoiceStatus(invoice.id()));
        assertEquals(List.of("PAID", "VOID"), invoices.get(invoice.id(), receptionist).items().stream().map(i -> i.status()).toList());
    }

    @Test
    void aPayosPaymentThatArrivesForAnItemVoidedByARefundRequestIsFlaggedForRefund() {
        var invoice = invoiceFor(appointment("CONFIRMED", null));
        UUID fee = invoice.items().getFirst().id();
        var attempt = payments.createOnlineAttempt(invoice.id(), new PaymentAttemptRequest(List.of(fee), PaymentChannel.ONLINE),
                patient, "r-" + UUID.randomUUID());
        refunds.requestRefund(fee, "Appointment cancelled while the QR was open");
        assertEquals(List.of("VOID"), invoices.get(invoice.id(), receptionist).items().stream().map(i -> i.status()).toList());

        long orderCode = orderCode(attempt.id());
        payments.handleVerifiedGatewayEvent(new PaymentGateway.VerifiedGatewayEvent("PAYOS", orderCode, PRICE_LONG, "VND",
                "link-" + orderCode, "ref-" + orderCode, "00", true));

        assertEquals("CONFIRMED", attemptStatus(attempt.id()));
        assertEquals("REFUND_PENDING", refundStatus(fee));
        assertEquals(List.of("APPLIED:REFUND_PENDING_ITEM_VOID"), eventOutcomes(orderCode));
        assertTrue(refunds.listRefunds(admin(), "PENDING", 0, 100).items().stream().anyMatch(r -> r.itemId().equals(fee)));
    }

    @Test
    void clinicCancellationOfAPaidAppointmentNotifiesThePatientOnce() {
        UUID item = paidFeeItem();
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        refunds.requestRefund(item, "Doctor on leave", start);
        refunds.requestRefund(item, "Doctor on leave", start); // already pending: no second notification
        var notices = jdbc.queryForList("select content from notifications where recipient_account_id = ? "
                + "and related_kind = 'PAYMENT' and related_id = ?", String.class, patientAccountId, invoiceOf(item));
        assertEquals(1, notices.size());
        assertTrue(notices.getFirst().contains("đã bị hủy bởi phòng khám"));
        jdbc.update("delete from notifications where recipient_account_id = ?", patientAccountId);
    }

    @Test
    void aServiceOrderIsPaidOnlyWhileItsItemIsPaidAndStandingAndPublishesOneEvent() {
        var invoice = invoiceFor(appointment("CONFIRMED", null));
        UUID item = invoice.items().getFirst().id();
        insertServiceOrderFor(item);
        assertFalse(billingQuery.isServiceOrderPaid(orderId));
        assertFalse(billingQuery.isServiceOrderPaid(UUID.randomUUID()));
        assertEquals(0, events.stream(ServiceOrderPaidEvent.class).count());

        payments.recordCounterPayment(invoice.id(), new CounterPaymentRequest(List.of(item), "CASH"), receptionist, "e-" + UUID.randomUUID());

        assertTrue(billingQuery.isServiceOrderPaid(orderId));
        var published = events.stream(ServiceOrderPaidEvent.class).toList();
        assertEquals(1, published.size());
        assertEquals(new ServiceOrderPaidEvent(orderId, item, invoice.id()), published.getFirst());

        refunds.requestRefund(item, "Order cannot be performed");
        assertFalse(billingQuery.isServiceOrderPaid(orderId));
    }

    @Test
    void aPayosPaymentOfAServiceOrderItemPublishesTheEvent() {
        var invoice = invoiceFor(appointment("CONFIRMED", null));
        UUID item = invoice.items().getFirst().id();
        insertServiceOrderFor(item);
        var attempt = payments.createOnlineAttempt(invoice.id(), patient, "pe-" + UUID.randomUUID());
        assertFalse(billingQuery.isServiceOrderPaid(orderId));
        long orderCode = orderCode(attempt.id());
        payments.handleVerifiedGatewayEvent(new PaymentGateway.VerifiedGatewayEvent("PAYOS", orderCode, PRICE_LONG, "VND",
                "link-" + orderCode, "ref-" + orderCode, "00", true));
        assertTrue(billingQuery.isServiceOrderPaid(orderId));
        assertEquals(1, events.stream(ServiceOrderPaidEvent.class).count());
    }

    private void insertServiceOrderFor(UUID itemId) {
        Instant now = Instant.now();
        jdbc.update("insert into visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id, status) "
                + "values (?, ?, 'WALK_IN', ?, ?, ?, 'OPEN')", visitId, patientProfileId, Timestamp.from(now), Timestamp.from(now),
                receptionistAccountId);
        UUID round = UUID.randomUUID();
        jdbc.update("insert into service_order_rounds (id,visit_id,round_number,ordered_by_doctor_id,ordered_at,created_at) "
                + "values (?,?,1,?,now(),now())", round, visitId, doctorProfileId);
        jdbc.update("insert into service_orders (id,order_code,ordering_round_id,visit_id,"
                + "patient_id,service_id,service_name_snapshot,service_type,requesting_doctor_id,clinical_reason,body_area,price_snapshot,"
                + "order_status,payment_status,cancel_reason,created_at,updated_at) values (?,?,?,?,?,?,'Order snapshot',"
                + "'IMAGING',?,'Pain','KNEE',125000,'ORDERED','PENDING_PAYMENT',null,now(),now())",
                orderId, "RF-" + orderId.toString().substring(0, 8), round, visitId, patientProfileId, serviceId, doctorProfileId);
        jdbc.update("update invoice_items set service_order_id = ? where id = ?", orderId, itemId);
    }
}
