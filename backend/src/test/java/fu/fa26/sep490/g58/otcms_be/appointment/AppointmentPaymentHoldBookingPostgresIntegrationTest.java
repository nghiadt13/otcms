package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentHoldSettlementService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Online booking with a payment hold: create, slot occupancy, cancel, lazy release and the pending-hold endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(PaymentHoldPostgresTestSupport.ClockConfiguration.class)
class AppointmentPaymentHoldBookingPostgresIntegrationTest extends PaymentHoldPostgresTestSupport {

    @Test
    void onlinePatientPayingServiceGetsAHoldWithDeadlineAndNoBookingNotification() throws Exception {
        String body = createOnline(patientA, paidService, SLOT).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString((String) json(body, "$.id"));

        assertEquals(NOW.plus(Duration.ofMinutes(15)), Instant.parse((String) json(body, "$.paymentDeadline")));
        assertEquals("AWAITING_PAYMENT", statusOf(id));
        assertEquals(1, count("select count(*) from appointment_audits where appointment_id = ? and action = 'CREATED' "
                + "and new_status = 'AWAITING_PAYMENT' and actor_type = 'PATIENT'", id));
        assertEquals(0, notificationsFor(id));

        // The owner reads the hold back by id, with the same deadline.
        String detail = mvc.perform(get("/api/v1/appointments/" + id).with(actor(patientA, AccountRole.PATIENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(NOW.plus(Duration.ofMinutes(15)), Instant.parse((String) json(detail, "$.paymentDeadline")));
    }

    @Test
    void freeServiceAndReceptionistBookingsAreConfirmedAtOnceWithTheBookingNotification() throws Exception {
        String free = createOnline(patientA, freeService, SLOT).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED")).andReturn().getResponse().getContentAsString();
        assertNull(json(free, "$.paymentDeadline"));
        UUID freeId = UUID.fromString((String) json(free, "$.id"));
        assertEquals(1, notificationsFor(freeId));

        // A receptionist books a PRICED service for the patient: no hold, trả tại quầy as before.
        String assisted = createByReceptionist(profileB, paidService, SLOT_2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED")).andReturn().getResponse().getContentAsString();
        UUID assistedId = UUID.fromString((String) json(assisted, "$.id"));
        assertNull(json(assisted, "$.paymentDeadline"));
        assertEquals("RECEPTIONIST_ASSISTED", jdbc.queryForObject(
                "select booking_source from appointments where id = ?", String.class, assistedId));
        assertEquals(1, count("select count(*) from appointment_audits where appointment_id = ? and action = 'CREATED' "
                + "and new_status = 'CONFIRMED'", assistedId));
        assertEquals(1, notificationsFor(assistedId));
    }

    @Test
    void holdOccupiesTheSlotUntilReleasedThenItCanBeBookedAgain() throws Exception {
        UUID hold = createHold(patientA, SLOT);

        createOnline(patientB, paidService, SLOT).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE"));

        mvc.perform(post("/api/v1/appointments/pending-hold/cancel").with(actor(patientA, AccountRole.PATIENT)).with(csrf()))
                .andExpect(status().isOk());
        assertEquals("PAYMENT_EXPIRED", statusOf(hold));

        createOnline(patientB, paidService, SLOT).andExpect(status().isCreated());
    }

    @Test
    void onlineBookingWithAFeeNeedsTheFullHoldWindowBeforeStart() throws Exception {
        clock.set(SLOT.minus(Duration.ofMinutes(10)));
        createOnline(patientA, paidService, SLOT).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BOOKING_TOO_SOON"));
        assertEquals(0, count("select count(*) from appointments where created_by_account_id = ?", patientA));

        // Exactly the hold window ahead is fine; free services have no such rule.
        clock.set(SLOT.minus(Duration.ofMinutes(15)));
        createOnline(patientA, paidService, SLOT).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));
        clock.set(SLOT.minus(Duration.ofMinutes(10)));
        createOnline(patientB, freeService, SLOT_2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void lapsedHoldDoesNotBlockTheNextBookingAndIsReleasedBySystemWithoutTrippingTheExcludeConstraint() throws Exception {
        UUID lapsed = createHold(patientA, SLOT);
        clock.set(NOW.plus(Duration.ofMinutes(15)).plusSeconds(5)); // swept only every 30s: still AWAITING in the DB
        assertEquals("AWAITING_PAYMENT", statusOf(lapsed));

        createOnline(patientB, paidService, SLOT).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"));

        assertEquals("PAYMENT_EXPIRED", statusOf(lapsed));
        Map<String, Object> audit = jdbc.queryForMap("select actor_type, actor_account_id, action, old_status, new_status, "
                + "reason from appointment_audits where appointment_id = ? and new_status = 'PAYMENT_EXPIRED'", lapsed);
        assertEquals("SYSTEM", audit.get("actor_type"));
        assertEquals(patientA, audit.get("actor_account_id"));
        assertEquals("STATUS_CHANGED", audit.get("action"));
        assertEquals("AWAITING_PAYMENT", audit.get("old_status"));
        assertEquals("Hết thời gian thanh toán", audit.get("reason"));
        verify(settlement).settle(lapsed, false);
    }

    @Test
    void holdWindowStartsAfterTheLapsedHoldsWereReleased() throws Exception {
        UUID lapsed = createHold(patientA, SLOT);
        clock.set(NOW.plus(Duration.ofMinutes(15)).plusSeconds(5));
        Instant afterRelease = NOW.plus(Duration.ofMinutes(16));
        // Releasing (gateway call) takes a while: the clock has moved on when the new hold is stamped.
        when(settlement.settle(eq(lapsed), eq(false))).thenAnswer(call -> {
            clock.set(afterRelease);
            return AppointmentHoldSettlementService.Outcome.NO_INVOICE;
        });

        String body = createOnline(patientB, paidService, SLOT).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(afterRelease.plus(Duration.ofMinutes(15)), Instant.parse((String) json(body, "$.paymentDeadline")));
    }

    @Test
    void newBookingReplacesTheAccountsOwnLiveHoldIncludingOnTheSameSlot() throws Exception {
        UUID first = createHold(patientA, SLOT);

        UUID second = createHold(patientA, SLOT_2);
        assertEquals("PAYMENT_EXPIRED", statusOf(first));
        assertEquals("AWAITING_PAYMENT", statusOf(second));
        assertEquals(1, count("select count(*) from appointment_audits where appointment_id = ? "
                + "and actor_type = 'PATIENT' and action = 'STATUS_CHANGED' and new_status = 'PAYMENT_EXPIRED'", first));
        verify(settlement).settle(first, true);

        // Booking the very slot just held is allowed: the old hold goes first.
        UUID third = createHold(patientA, SLOT_2);
        assertEquals("PAYMENT_EXPIRED", statusOf(second));
        assertEquals("AWAITING_PAYMENT", statusOf(third));
        assertEquals(1, count("select count(*) from appointments where created_by_account_id = ? "
                + "and status = 'AWAITING_PAYMENT'", patientA));
    }

    @Test
    void otherAccountsLiveHoldIsNotTouchedByANewBooking() throws Exception {
        UUID others = createHold(patientB, SLOT);
        createHold(patientA, SLOT_2);
        assertEquals("AWAITING_PAYMENT", statusOf(others));
    }

    @Test
    void holdCanBeCancelledCloseToStartButConfirmedStillCannot() throws Exception {
        clock.set(SLOT.minus(Duration.ofMinutes(20)));
        UUID hold = createHold(patientA, SLOT);
        UUID confirmed = insertAppointment(patientA, profileA, freeService, SLOT_2, "CONFIRMED", null, "PATIENT_ONLINE");

        mvc.perform(post("/api/v1/appointments/" + hold + "/cancel").with(actor(patientA, AccountRole.PATIENT)).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAYMENT_EXPIRED"));
        assertEquals("PAYMENT_EXPIRED", statusOf(hold));
        verify(settlement).settle(hold, true);
        assertEquals(1, count("select count(*) from appointment_audits where appointment_id = ? and actor_type = 'PATIENT' "
                + "and actor_account_id = ? and action = 'STATUS_CHANGED' and old_status = 'AWAITING_PAYMENT' "
                + "and new_status = 'PAYMENT_EXPIRED'", hold, patientA));

        mvc.perform(post("/api/v1/appointments/" + confirmed + "/cancel").with(actor(patientA, AccountRole.PATIENT)).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("APPOINTMENT_CHANGE_TOO_LATE"));
        assertEquals("CONFIRMED", statusOf(confirmed));
    }

    @Test
    void onlyTheBookingPatientCanCancelAHoldAndStaffNeverSeeIt() throws Exception {
        UUID hold = createHold(patientA, SLOT);

        mvc.perform(post("/api/v1/appointments/" + hold + "/cancel").with(actor(patientB, AccountRole.PATIENT)).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_FOUND"));
        mvc.perform(post("/api/v1/appointments/" + hold + "/cancel").with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"staff\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_FOUND"));
        assertEquals("AWAITING_PAYMENT", statusOf(hold));
    }

    @Test
    void cancellingAHoldThatBillingFoundPaidIsRejectedAndNothingChanges() throws Exception {
        UUID hold = createHold(patientA, SLOT);
        when(settlement.settle(eq(hold), eq(true))).thenReturn(AppointmentHoldSettlementService.Outcome.PAID);

        mvc.perform(post("/api/v1/appointments/" + hold + "/cancel").with(actor(patientA, AccountRole.PATIENT)).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));
        assertEquals("AWAITING_PAYMENT", statusOf(hold));
    }

    @Test
    void pendingHoldCancelIsIdempotentOwnerOnlyAndReportsAlreadyPaid() throws Exception {
        String none = mvc.perform(post("/api/v1/appointments/pending-hold/cancel")
                        .with(actor(patientA, AccountRole.PATIENT)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledCount").value(0))
                .andExpect(jsonPath("$.alreadyPaid").value(false)).andReturn().getResponse().getContentAsString();
        assertTrue(none.contains("cancelledCount"));

        UUID mine = createHold(patientA, SLOT);
        UUID theirs = createHold(patientB, SLOT_2);
        mvc.perform(post("/api/v1/appointments/pending-hold/cancel").with(actor(patientA, AccountRole.PATIENT)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledCount").value(1))
                .andExpect(jsonPath("$.alreadyPaid").value(false));
        assertEquals("PAYMENT_EXPIRED", statusOf(mine));
        assertEquals("AWAITING_PAYMENT", statusOf(theirs));
        verify(settlement).settle(mine, true);

        mvc.perform(post("/api/v1/appointments/pending-hold/cancel").with(actor(patientA, AccountRole.PATIENT)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledCount").value(0));

        when(settlement.settle(eq(theirs), eq(true))).thenReturn(AppointmentHoldSettlementService.Outcome.PAID);
        mvc.perform(post("/api/v1/appointments/pending-hold/cancel").with(actor(patientB, AccountRole.PATIENT)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledCount").value(0))
                .andExpect(jsonPath("$.alreadyPaid").value(true));
        assertEquals("AWAITING_PAYMENT", statusOf(theirs));
    }

    @Test
    void pendingHoldCancelNeedsCsrfAndAPatientAccount() throws Exception {
        mvc.perform(post("/api/v1/appointments/pending-hold/cancel").with(actor(patientA, AccountRole.PATIENT)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/appointments/pending-hold/cancel").with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/appointments/pending-hold/cancel").with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void awaitingPaymentAppointmentCannotBeCheckedInRescheduledOrReassigned() throws Exception {
        UUID hold = createHold(patientA, SLOT);

        mvc.perform(patch("/api/v1/appointments/" + hold + "/reschedule").with(actor(patientA, AccountRole.PATIENT)).with(csrf())
                        .contentType("application/json")
                        .content("{\"startTime\":\"" + SLOT_3 + "\",\"reason\":\"later\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));
        mvc.perform(post("/api/v1/appointments/" + hold + "/check-in").with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                        .header("Idempotency-Key", "hold-check-in-1").contentType("application/json")
                        .content("{\"verifiedPatientProfileId\":\"" + profileA + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));
        mvc.perform(post("/api/v1/appointments/" + hold + "/reassign").with(actor(receptionist, AccountRole.RECEPTIONIST)).with(csrf())
                        .contentType("application/json")
                        .content("{\"doctorId\":\"" + UUID.randomUUID() + "\",\"reason\":\"swap\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_CHANGEABLE"));
        assertEquals("AWAITING_PAYMENT", statusOf(hold));
        assertEquals(0, count("select count(*) from appointment_audits where appointment_id = ? and action <> 'CREATED'", hold));
    }
}
