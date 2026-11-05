package fu.fa26.sep490.g58.otcms_be.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import fu.fa26.sep490.g58.otcms_be.billing.exception.BillingException;
import fu.fa26.sep490.g58.otcms_be.billing.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.billing.model.dto.response.InvoiceResponse;
import fu.fa26.sep490.g58.otcms_be.billing.repository.*;
import fu.fa26.sep490.g58.otcms_be.billing.service.*;
import fu.fa26.sep490.g58.otcms_be.billing.service.impl.BillingReadServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientBillingProfileService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;

class BillingReadServiceTest {
    final BillingReadRepository reads = mock(BillingReadRepository.class);
    final AuthorizePatientBillingProfileService patients = mock(AuthorizePatientBillingProfileService.class);
    final InvoiceService invoices = mock(InvoiceService.class);
    final PaymentAttemptRepository payments = mock(PaymentAttemptRepository.class);
    final BillingReadService service = new BillingReadServiceImpl(reads, patients, invoices, payments);
    final UUID account = UUID.randomUUID(), patient = UUID.randomUUID(), dependent = UUID.randomUUID(), invoiceId = UUID.randomUUID();
    AuthenticatedAccount actor(AccountRole role) { return new AuthenticatedAccount(account, role, null); }
    void code(String code, Runnable action) { assertEquals(code, assertThrows(BillingException.class, action::run).code()); }
    @Test void noAnonymousDoctorOrTechnicianBillingAccess() {
        code("ACCESS_DENIED", () -> service.payments(null, null, null, null, 0, 20));
        for (var role : List.of(AccountRole.DOCTOR, AccountRole.IMAGING_TECHNICIAN)) {
            code("ACCESS_DENIED", () -> service.payments(actor(role), null, null, null, 0, 20));
            code("ACCESS_DENIED", () -> service.unpaid(actor(role), 0, 20));
        }
        verifyNoInteractions(reads, payments, invoices);
    }
    @Test void patientFilterMustBelongToAuthorizedProfiles() {
        when(patients.accessibleProfileIds(account)).thenReturn(List.of(patient, dependent));
        code("ACCESS_DENIED", () -> service.payments(actor(AccountRole.PATIENT), UUID.randomUUID(), null, null, 0, 20));
        verifyNoInteractions(reads);
    }
    @Test void patientHistoryNarrowsDependentScopeAndConvertsClinicDateBoundaries() {
        when(patients.accessibleProfileIds(account)).thenReturn(List.of(patient, dependent));
        when(reads.completedPayments(anyBoolean(), any(), any(), any(), any())).thenReturn(Page.empty());
        var date = LocalDate.of(2026, 11, 5);
        service.payments(actor(AccountRole.PATIENT), dependent, date, date, 0, 20);
        verify(reads).completedPayments(false, List.of(dependent), Instant.parse("2026-11-04T17:00:00Z"),
                Instant.parse("2026-11-05T17:00:00Z"), PageRequest.of(0, 20));
    }
    @Test void staffHasClinicReadScopeAndCanNarrowPatient() {
        when(reads.completedPayments(anyBoolean(), any(), any(), any(), any())).thenReturn(Page.empty());
        for (var role : List.of(AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.OWNER)) {
            service.payments(actor(role), null, null, null, 0, 20);
        }
        verify(reads, times(3)).completedPayments(true, List.of(), null, null, PageRequest.of(0, 20));
        service.payments(actor(AccountRole.ADMIN), patient, null, null, 1, 10);
        verify(reads).completedPayments(false, List.of(patient), null, null, PageRequest.of(1, 10));
        verifyNoInteractions(patients);
    }
    @Test void malformedRangeAndUnboundedPageRejected() {
        var staff = actor(AccountRole.RECEPTIONIST);
        code("MSG-39", () -> service.payments(staff, null, LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1), 0, 20));
        code("MSG-39", () -> service.unpaid(staff, -1, 20));
        code("MSG-39", () -> service.unpaid(staff, 0, 101));
        code("MSG-39", () -> service.unpaid(staff, Integer.MAX_VALUE, 100));
        verifyNoInteractions(reads);
    }
    @Test void onlyStaffCanReadOutstandingBalancesAndPartialAmountsPreserved() {
        code("ACCESS_DENIED", () -> service.unpaid(actor(AccountRole.PATIENT), 0, 20));
        var row = mock(BillingReadRepository.BalanceRow.class);
        when(row.getTotalAmount()).thenReturn(new BigDecimal("100000"));
        when(row.getPaidAmount()).thenReturn(new BigDecimal("30000"));
        when(reads.unpaidInvoices(PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(row)));
        assertEquals(new BigDecimal("70000"), service.unpaid(actor(AccountRole.OWNER), 0, 20).items().getFirst().outstandingBalance());
    }
    InvoiceResponse invoice() {
        return new InvoiceResponse(invoiceId, "INV-1", patient, null, null, "OPEN",
                new BigDecimal("100000"), new BigDecimal("30000"), new BigDecimal("70000"), Instant.now(),
                List.of(new InvoiceResponse.Item(1, UUID.randomUUID(), "Khám chỉnh hình", 1, new BigDecimal("100000"), new BigDecimal("100000"))));
    }
    @Test void receiptRepresentsOneConfirmedPartialPaymentRatherThanInvoiceTotal() {
        UUID paymentId = UUID.randomUUID();
        var payment = PaymentAttemptJpaEntity.counter(paymentId, invoiceId, new BigDecimal("30000"), account,
                "key", "fingerprint", "CASH", Instant.parse("2026-11-05T00:00:00Z"));
        when(payments.findById(paymentId)).thenReturn(Optional.of(payment));
        when(invoices.get(invoiceId, actor(AccountRole.RECEPTIONIST))).thenReturn(invoice());
        // AuthenticatedAccount is an identity projection, so match the exact request principal.
        var staff = actor(AccountRole.RECEPTIONIST);
        when(invoices.get(invoiceId, staff)).thenReturn(invoice());
        String receipt = service.receiptExport(staff, paymentId);
        assertTrue(receipt.contains(paymentId.toString())); assertTrue(receipt.contains("30000 VND"));
        assertFalse(receipt.contains("100000"));
        verify(payments, never()).save(any());
    }
    @Test void unconfirmedPaymentCannotGenerateReceipt() {
        UUID id = UUID.randomUUID();
        when(payments.findById(id)).thenReturn(Optional.of(PaymentAttemptJpaEntity.pendingOnline(id, invoiceId,
                BigDecimal.TEN, account, "key", "hash", 1)));
        code("MSG-42", () -> service.receiptExport(actor(AccountRole.RECEPTIONIST), id));
        verifyNoInteractions(invoices);
    }
    @Test void exportsRequireReceptionistAndUseInvoiceSnapshots() {
        for (var role : List.of(AccountRole.PATIENT, AccountRole.ADMIN, AccountRole.OWNER, AccountRole.DOCTOR)) {
            code("ACCESS_DENIED", () -> service.invoiceExport(actor(role), invoiceId));
            code("ACCESS_DENIED", () -> service.receiptExport(actor(role), UUID.randomUUID()));
        }
        var staff = actor(AccountRole.RECEPTIONIST);
        when(invoices.get(invoiceId, staff)).thenReturn(invoice());
        var content = service.invoiceExport(staff, invoiceId);
        assertTrue(content.contains("Khám chỉnh hình")); assertTrue(content.contains("Outstanding: 70000 VND"));
        verifyNoInteractions(payments);
    }
}
