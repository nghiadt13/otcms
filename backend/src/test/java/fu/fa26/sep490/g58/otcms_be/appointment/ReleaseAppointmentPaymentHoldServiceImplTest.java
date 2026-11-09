package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentBooking;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService.Actor;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReleaseAppointmentPaymentHoldService.Outcome;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.AppointmentHoldExpiryTransaction;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.ReleaseAppointmentPaymentHoldServiceImpl;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentHoldSettlementService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReleaseAppointmentPaymentHoldServiceImplTest {
    private final AppointmentHoldSettlementService settlement = mock(AppointmentHoldSettlementService.class);
    private final AppointmentHoldExpiryTransaction expiry = mock(AppointmentHoldExpiryTransaction.class);
    private final ReleaseAppointmentPaymentHoldServiceImpl service =
            new ReleaseAppointmentPaymentHoldServiceImpl(settlement, expiry);
    private final UUID id = UUID.randomUUID();
    private final Actor actor = Actor.system("trace");

    @Test
    void paidAndStillPendingNeverTouchTheAppointmentRow() {
        when(settlement.settle(id, true)).thenReturn(AppointmentHoldSettlementService.Outcome.PAID);
        when(settlement.settle(id, false)).thenReturn(AppointmentHoldSettlementService.Outcome.STILL_PENDING);

        assertEquals(Outcome.PAID, service.release(id, true, actor));
        assertEquals(Outcome.STILL_PENDING, service.release(id, false, actor));
        verifyNoInteractions(expiry);
    }

    @Test
    void voidedAndNoInvoiceReleaseTheHoldAfterBillingSettled() {
        when(settlement.settle(id, true)).thenReturn(AppointmentHoldSettlementService.Outcome.VOIDED);
        when(expiry.expire(id, actor)).thenReturn(Outcome.RELEASED);

        assertEquals(Outcome.RELEASED, service.release(id, true, actor));

        when(settlement.settle(id, false)).thenReturn(AppointmentHoldSettlementService.Outcome.NO_INVOICE);
        when(expiry.expire(id, actor)).thenReturn(Outcome.NOT_AWAITING);
        assertEquals(Outcome.NOT_AWAITING, service.release(id, false, actor));
        // Lock order: billing (invoice) first, the appointment row only afterwards.
        InOrder order = inOrder(settlement, expiry);
        order.verify(settlement).settle(id, true);
        order.verify(expiry).expire(id, actor);
    }

    @Test
    void settlementFailurePropagatesAndLeavesTheHoldAlone() {
        when(settlement.settle(id, false)).thenThrow(new IllegalStateException("gateway down"));

        assertThrows(IllegalStateException.class, () -> service.release(id, false, actor));
        verify(expiry, never()).expire(any(), any());
    }

    @Test
    void rejectsMissingIdentityOrAuditData() {
        assertThrows(IllegalArgumentException.class, () -> service.release(null, true, actor));
        assertThrows(IllegalArgumentException.class, () -> service.release(id, true, null));
        assertThrows(IllegalArgumentException.class, () -> service.release(id, true, new Actor(null, "SYSTEM", " ", "trace")));
        verifyNoInteractions(settlement, expiry);
    }

    @Test
    void onlyOnlinePatientBookingsCanHoldASlot() {
        Instant now = Instant.parse("2026-10-14T05:00:00Z");
        Instant start = now.plusSeconds(86400);
        UUID account = UUID.randomUUID();
        UUID deadlineBase = UUID.randomUUID();
        var online = new AppointmentBooking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), deadlineBase,
                AppointmentBooking.BookingSource.PATIENT_ONLINE, "x", start, start.plusSeconds(1800), 30, 0, account,
                AppointmentBooking.ActorType.PATIENT, now, "c", null, null, null, null, now.plusSeconds(900));
        assertEquals("AWAITING_PAYMENT", online.initialStatus());

        assertThrows(IllegalArgumentException.class, () -> new AppointmentBooking(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), deadlineBase, AppointmentBooking.BookingSource.RECEPTIONIST_ASSISTED, "x", start,
                start.plusSeconds(1800), 30, 0, account, AppointmentBooking.ActorType.RECEPTIONIST, now, "c",
                null, null, null, null, now.plusSeconds(900)));
        assertThrows(IllegalArgumentException.class, () -> new AppointmentBooking(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), deadlineBase, AppointmentBooking.BookingSource.PATIENT_ONLINE, "x", start,
                start.plusSeconds(1800), 30, 0, account, AppointmentBooking.ActorType.PATIENT, now, "c",
                null, null, null, null, now));
        var confirmed = new AppointmentBooking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), deadlineBase,
                AppointmentBooking.BookingSource.RECEPTIONIST_ASSISTED, "x", start, start.plusSeconds(1800), 30, 0,
                account, AppointmentBooking.ActorType.RECEPTIONIST, now, "c");
        assertEquals("CONFIRMED", confirmed.initialStatus());
        assertTrue(confirmed.paymentDeadline() == null);
    }
}
