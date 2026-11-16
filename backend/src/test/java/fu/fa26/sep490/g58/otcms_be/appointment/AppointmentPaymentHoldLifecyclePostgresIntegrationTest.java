package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.activitylog.repository.ActivityLogRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Filter;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Scope;
import fu.fa26.sep490.g58.otcms_be.appointment.model.ScopeType;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentQueryRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentBillingSourceService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentPaymentHoldQueryService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ConfirmAppointmentPaymentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ConfirmAppointmentPaymentService.Result;
import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.ExpireUnpaidAppointmentsJob;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentHoldSettlementService.Outcome;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.ReportingRepository;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hold lifecycle: visibility, confirmPaid, the sweeper, billing/impact read models and reporting. */
@SpringBootTest
@AutoConfigureMockMvc
@LocalPostgresTest
@Import(PaymentHoldPostgresTestSupport.ClockConfiguration.class)
class AppointmentPaymentHoldLifecyclePostgresIntegrationTest extends PaymentHoldPostgresTestSupport {
    @Autowired AppointmentQueryRepository queries;
    @Autowired ConfirmAppointmentPaymentService confirmPayment;
    @Autowired ExpireUnpaidAppointmentsJob sweeper;
    @Autowired AppointmentBillingSourceService billingSource;
    @Autowired AppointmentMutationImpactService impact;
    @Autowired AppointmentPaymentHoldQueryService holdQueries;
    @Autowired ReportingRepository reporting;
    @Autowired ActivityLogRepository activityLog;

    // ---- visibility ------------------------------------------------------------------------------------------

    @Test
    void holdsAreInvisibleEverywhereExceptByIdToTheOwningPatient() throws Exception {
        UUID hold = insertHold(patientA, profileA, SLOT, NOW.plus(Duration.ofMinutes(15)));
        UUID expired = insertAppointment(patientA, profileA, paidService, SLOT_2, "PAYMENT_EXPIRED", null, "PATIENT_ONLINE");
        UUID confirmed = insertAppointment(patientA, profileA, freeService, SLOT_3, "CONFIRMED", null, "PATIENT_ONLINE");
        Set<String> hidden = Set.of(hold.toString(), expired.toString());

        for (String window : List.of("UPCOMING", "PAST")) {
            Set<String> ids = ids(mvc.perform(get("/api/v1/appointments?window=" + window)
                    .with(actor(patientA, AccountRole.PATIENT))).andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString());
            assertTrue(Set.copyOf(ids).stream().noneMatch(hidden::contains), window + " leaked a hold: " + ids);
            if (window.equals("UPCOMING")) assertTrue(ids.contains(confirmed.toString()));
        }
        Set<String> all = ids(mvc.perform(get("/api/v1/appointments").with(actor(patientA, AccountRole.PATIENT)))
                .andReturn().getResponse().getContentAsString());
        assertTrue(all.contains(confirmed.toString()));
        assertTrue(all.stream().noneMatch(hidden::contains));
        // An explicit status filter cannot reach them either.
        mvc.perform(get("/api/v1/appointments?status=AWAITING_PAYMENT").with(actor(patientA, AccountRole.PATIENT)))
                .andExpect(status().isBadRequest());

        // Receptionist list and detail.
        Set<String> staffIds = ids(mvc.perform(get("/api/v1/appointments?patientProfileId=" + profileA)
                .with(actor(receptionist, AccountRole.RECEPTIONIST))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        assertEquals(Set.of(confirmed.toString()), staffIds);
        mvc.perform(get("/api/v1/appointments/" + hold).with(actor(receptionist, AccountRole.RECEPTIONIST)))
                .andExpect(status().isNotFound());

        // The owner (and only the owner) reads them by id; another patient does not.
        mvc.perform(get("/api/v1/appointments/" + hold).with(actor(patientA, AccountRole.PATIENT))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/appointments/" + expired).with(actor(patientA, AccountRole.PATIENT))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/appointments/" + hold).with(actor(patientB, AccountRole.PATIENT))).andExpect(status().isNotFound());

        // Doctor and clinic (admin) scopes straight from the shared query repository.
        var noFilter = new Filter(null, null, null, doctorProfile, null);
        var clinic = queries.find(new Scope(ScopeType.CLINIC, null), noFilter, 0, 50).items();
        assertEquals(Set.of(confirmed), clinic.stream().map(r -> r.id()).collect(Collectors.toSet()));
        var doctor = queries.find(new Scope(ScopeType.DOCTOR, doctorProfile), new Filter(null, null, null, null, null), 0, 50).items();
        assertEquals(Set.of(confirmed), doctor.stream().map(r -> r.id()).collect(Collectors.toSet()));
        assertTrue(queries.findScoped(new Scope(ScopeType.CLINIC, null), hold).isEmpty());
        assertTrue(queries.findScoped(new Scope(ScopeType.DOCTOR, doctorProfile), hold).isEmpty());
        assertTrue(queries.findScoped(Scope.patient(Set.of(profileA)), hold).isPresent());
    }

    @Test
    void reportsNeverCountHoldStates() {
        LocalDate day = LocalDate.of(2031, 3, 3); // far from every other fixture
        Instant base = Instant.parse("2031-03-03T03:00:00Z");
        var period = new ReportingPeriod(day, day);
        insertAppointment(patientA, profileA, paidService, base, "AWAITING_PAYMENT", base.minusSeconds(60), "PATIENT_ONLINE");
        insertAppointment(patientA, profileA, paidService, base.plusSeconds(1800), "PAYMENT_EXPIRED", null, "PATIENT_ONLINE");
        insertAppointment(patientA, profileA, freeService, base.plusSeconds(3600), "CONFIRMED", null, "PATIENT_ONLINE");
        insertAppointment(patientA, profileA, freeService, base.plusSeconds(5400), "CANCELLED", null, "PATIENT_ONLINE");

        Map<String, Long> counts = reporting.appointmentCounts(period);
        assertEquals(Map.of("BOOKED", 1L, "CANCELLED", 1L), counts);
        assertEquals(2L, reporting.daily("APPOINTMENTS", period).stream().mapToLong(d -> d.count()).sum());
    }

    @Test
    void activityLogHidesHoldAuditsUnlessThePaymentConfirmedTheBooking() throws Exception {
        UUID released = createHold(patientA, SLOT);
        mvc.perform(post("/api/v1/appointments/pending-hold/cancel").with(actor(patientA, AccountRole.PATIENT)).with(csrf()))
                .andExpect(status().isOk());
        UUID paid = createHold(patientA, SLOT_2);
        UUID live = createHold(patientB, SLOT_3);
        assertEquals(Result.CONFIRMED, confirmPayment.confirmPaid(paid));
        UUID releasedAudit = jdbc.queryForObject("select id from audit_logs where source = 'appointment_audits' and entity_id = ? limit 1", UUID.class, released);

        // Released hold: CREATED + STATUS_CHANGED rows exist in the table but none is exposed (rows and total agree).
        assertEquals(2, count("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ?", released));
        var releasedPage = activityLog.list(new ActivityLogRepository.Criteria(null, null, "", released.toString(), "", 0, 50));
        assertEquals(0, releasedPage.items().size());
        assertEquals(0, releasedPage.totalItems());
        assertTrue(activityLog.find("appointment:" + releasedAudit).isEmpty());
        // A live hold is hidden too.
        assertEquals(0, activityLog.list(new ActivityLogRepository.Criteria(null, null, "", live.toString(), "", 0, 50)).totalItems());
        // A hold that was paid is a real booking: both its rows show.
        var paidPage = activityLog.list(new ActivityLogRepository.Criteria(null, null, "", paid.toString(), "", 0, 50));
        assertEquals(2, paidPage.items().size());
        assertEquals(2, paidPage.totalItems());
    }

    // ---- confirmPaid -----------------------------------------------------------------------------------------

    @Test
    void confirmPaidConfirmsOnceAuditsAsSystemAndNotifiesExactlyOnce() {
        UUID hold = insertHold(patientA, profileA, SLOT, NOW.plus(Duration.ofMinutes(15)));

        assertEquals(Result.CONFIRMED, confirmPayment.confirmPaid(hold));
        assertEquals("CONFIRMED", statusOf(hold));
        assertNull(jdbc.queryForObject("select payment_deadline from appointments where id = ?", java.sql.Timestamp.class, hold));
        Map<String, Object> audit = jdbc.queryForMap("select actor_type, actor_account_id, action, before_state->>'status' as old_status, after_state->>'status' as new_status "
                + "from audit_logs where source = 'appointment_audits' and entity_id = ? and after_state->>'status' = 'CONFIRMED'", hold);
        assertEquals("SYSTEM", audit.get("actor_type"));
        assertEquals(patientA, audit.get("actor_account_id"));
        assertEquals("STATUS_CHANGED", audit.get("action"));
        assertEquals("AWAITING_PAYMENT", audit.get("old_status"));
        assertEquals(1, notificationsFor(hold));

        assertEquals(Result.ALREADY_CONFIRMED, confirmPayment.confirmPaid(hold));
        assertEquals(1, notificationsFor(hold));
        assertEquals(1, count("select count(*) from audit_logs where source = 'appointment_audits' and entity_id = ? and after_state->>'status' = 'CONFIRMED'", hold));
    }

    @Test
    void confirmPaidReportsExpectedStatesWithoutThrowing() {
        UUID released = insertAppointment(patientA, profileA, paidService, SLOT, "PAYMENT_EXPIRED", null, "PATIENT_ONLINE");
        UUID cancelled = insertAppointment(patientA, profileA, paidService, SLOT_2, "CANCELLED", null, "PATIENT_ONLINE");
        UUID confirmed = insertAppointment(patientA, profileA, freeService, SLOT_3, "CONFIRMED", null, "RECEPTIONIST_ASSISTED");

        assertEquals(Result.NOT_AWAITING, confirmPayment.confirmPaid(released));
        assertEquals(Result.NOT_AWAITING, confirmPayment.confirmPaid(cancelled));
        assertEquals(Result.ALREADY_CONFIRMED, confirmPayment.confirmPaid(confirmed));
        assertEquals(Result.NOT_AWAITING, confirmPayment.confirmPaid(UUID.randomUUID()));
        assertEquals(Result.NOT_AWAITING, confirmPayment.confirmPaid(null));
        assertEquals("PAYMENT_EXPIRED", statusOf(released));
        assertEquals("CANCELLED", statusOf(cancelled));
        assertEquals(0, notificationsFor(released) + notificationsFor(cancelled) + notificationsFor(confirmed));
    }

    // ---- sweeper ---------------------------------------------------------------------------------------------

    @Test
    void sweeperReleasesLapsedHoldsAndOneFailingAppointmentDoesNotStopTheBatch() {
        Instant lapsedAt = NOW.minus(Duration.ofMinutes(1));
        UUID broken = insertHold(patientA, profileA, SLOT, lapsedAt);
        UUID pending = insertHold(patientA, profileA, SLOT_2, lapsedAt.plusSeconds(1));
        UUID paid = insertHold(patientB, profileB, SLOT_3, lapsedAt.plusSeconds(2));
        UUID plain = insertHold(patientB, profileB, SLOT_3.plusSeconds(1800), lapsedAt.plusSeconds(3));
        UUID alive = insertHold(patientB, profileB, SLOT_3.plusSeconds(3600), NOW.plus(Duration.ofMinutes(5)));
        when(settlement.settle(eq(broken), eq(false))).thenThrow(new IllegalStateException("gateway down"));
        when(settlement.settle(eq(pending), eq(false))).thenReturn(Outcome.STILL_PENDING);
        when(settlement.settle(eq(paid), eq(false))).thenReturn(Outcome.PAID);
        when(settlement.settle(eq(plain), eq(false))).thenReturn(Outcome.VOIDED);

        assertEquals(1, sweeper.sweepOnce());

        assertEquals("AWAITING_PAYMENT", statusOf(broken));
        assertEquals("AWAITING_PAYMENT", statusOf(pending));
        assertEquals("AWAITING_PAYMENT", statusOf(paid)); // billing confirms through the hook, not the sweeper
        assertEquals("PAYMENT_EXPIRED", statusOf(plain));
        assertEquals("AWAITING_PAYMENT", statusOf(alive));
        Map<String, Object> audit = jdbc.queryForMap("select actor_type, actor_account_id, metadata->>'reason' as reason from audit_logs "
                + "where source = 'appointment_audits' and entity_id = ? and after_state->>'status' = 'PAYMENT_EXPIRED'", plain);
        assertEquals("SYSTEM", audit.get("actor_type"));
        assertEquals(patientB, audit.get("actor_account_id"));
        assertEquals("Hết thời gian thanh toán", audit.get("reason"));

        // Next run, once billing recovers, finishes the job; a second run is a no-op for the released one.
        when(settlement.settle(eq(broken), eq(false))).thenReturn(Outcome.NO_INVOICE);
        assertEquals(1, sweeper.sweepOnce());
        assertEquals("PAYMENT_EXPIRED", statusOf(broken));
        assertEquals(0, sweeper.sweepOnce());
    }

    // ---- read models used by billing and admin safeguards -----------------------------------------------------

    @Test
    void billingSourceExposesTheHoldStatusAndHoldQueriesAnswerForBilling() {
        Instant deadline = NOW.plus(Duration.ofMinutes(15));
        UUID hold = insertHold(patientA, profileA, SLOT, deadline);
        UUID released = insertAppointment(patientA, profileA, paidService, SLOT_2, "PAYMENT_EXPIRED", null, "PATIENT_ONLINE");
        UUID confirmed = insertAppointment(patientA, profileA, freeService, SLOT_3, "CONFIRMED", null, "PATIENT_ONLINE");

        var source = billingSource.requireSource(hold);
        assertEquals("AWAITING_PAYMENT", source.status());
        assertEquals(profileA, source.patientProfileId());

        assertEquals(deadline, holdQueries.findLiveHoldDeadline(hold).orElseThrow());
        assertTrue(holdQueries.findLiveHoldDeadline(released).isEmpty());
        assertTrue(holdQueries.findLiveHoldDeadline(confirmed).isEmpty());
        assertEquals(Set.of(hold, released),
                holdQueries.findHoldOrReleasedAppointmentIds(List.of(hold, released, confirmed, UUID.randomUUID())));
        assertTrue(holdQueries.findHoldOrReleasedAppointmentIds(List.of()).isEmpty());
    }

    @Test
    void liveHoldCountsInMutationImpactUntilItsDeadlineOrRelease() {
        Instant deadline = NOW.plus(Duration.ofMinutes(15));
        UUID hold = insertHold(patientA, profileA, SLOT, deadline);
        Instant from = SLOT.minusSeconds(60);
        Instant to = SLOT.plusSeconds(60);

        assertEquals(1, impact.countBookingsForAssignment(doctorProfile, paidService));
        assertEquals(1, impact.findProtectedBookingIntervals(doctorProfile).size());

        clock.set(deadline.plusSeconds(1));
        assertEquals(0, impact.countBookingsForAssignment(doctorProfile, paidService));
        assertTrue(impact.findProtectedBookingIntervals(doctorProfile).isEmpty());

        clock.set(NOW);
        jdbc.update("update appointments set status = 'PAYMENT_EXPIRED' where id = ?", hold);
        assertEquals(0, impact.countBookingsForAssignment(doctorProfile, paidService));
        assertFalse(impact.findProtectedBookingIntervals(doctorProfile).stream().anyMatch(i -> true));
    }

    private static Set<String> ids(String pageJson) {
        Object items = json(pageJson, "$.items[*].id");
        @SuppressWarnings("unchecked")
        List<String> list = items == null ? List.of() : (List<String>) items;
        return Set.copyOf(list);
    }
}
