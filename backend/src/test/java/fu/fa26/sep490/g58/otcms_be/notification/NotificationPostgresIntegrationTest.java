package fu.fa26.sep490.g58.otcms_be.notification;

import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.notification.model.*;
import fu.fa26.sep490.g58.otcms_be.notification.model.entity.NotificationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.notification.repository.NotificationRepository;
import fu.fa26.sep490.g58.otcms_be.notification.service.*;
import fu.fa26.sep490.g58.otcms_be.appointment.service.*;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInVisitLifecycleService;
import fu.fa26.sep490.g58.otcms_be.review.service.ReviewService;
import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.SubmitReviewRequest;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @LocalPostgresTest @Transactional @AutoConfigureMockMvc
@Import(NotificationPostgresIntegrationTest.FixedClock.class)
class NotificationPostgresIntegrationTest {
    static final Instant NOW = Instant.parse("2026-11-01T04:00:00Z"), SLOT = Instant.parse("2026-11-03T03:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationRepository repository;
    @Autowired MyNotificationService inbox;
    @Autowired NotificationPublisher publisher;
    @Autowired CreateAppointmentService booking;
    @Autowired ReassignAppointmentService reassign;
    @Autowired CancelAppointmentService cancellations;
    @Autowired RescheduleAppointmentService reschedules;
    @Autowired WalkInVisitLifecycleService lifecycle;
    @Autowired ReviewService reviews;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), admin = UUID.randomUUID(), disabledAdmin = UUID.randomUUID(), owner = UUID.randomUUID();
    final UUID receptionist = UUID.randomUUID(), doctorAccount = UUID.randomUUID(), replacementAccount = UUID.randomUUID();
    final UUID profile = UUID.randomUUID(), otherProfile = UUID.randomUUID(), unlinked = UUID.randomUUID();
    final UUID doctor = UUID.randomUUID(), replacement = UUID.randomUUID(), serviceId = UUID.randomUUID();
    final UUID visit = UUID.randomUUID(), foreignVisit = UUID.randomUUID(), openVisit = UUID.randomUUID(), unlinkedVisit = UUID.randomUUID();
    final AuthenticatedAccount patient = new AuthenticatedAccount(a, AccountRole.PATIENT, UUID.randomUUID());
    final AuthenticatedAccount other = new AuthenticatedAccount(b, AccountRole.PATIENT, UUID.randomUUID());
    final AuthenticatedAccount administrator = new AuthenticatedAccount(admin, AccountRole.ADMIN, UUID.randomUUID());
    @BeforeEach void fixtures() {
        account(a, "PATIENT", "ACTIVE"); account(b, "PATIENT", "ACTIVE"); account(admin, "ADMIN", "ACTIVE");
        account(disabledAdmin, "ADMIN", "DISABLED"); account(owner, "OWNER", "ACTIVE");
        account(receptionist, "RECEPTIONIST", "ACTIVE"); account(doctorAccount, "DOCTOR", "ACTIVE"); account(replacementAccount, "DOCTOR", "ACTIVE");
        profile(profile, a); profile(otherProfile, b); profile(unlinked, null);
        jdbc.update("insert into clinic_services(id,name,duration_minutes,buffer_minutes,online_bookable,active) values (?, 'Notification test examination',30,0,true,true)", serviceId);
        doctor(doctor, doctorAccount); doctor(replacement, replacementAccount);
        visit(visit, profile, "CLOSED"); visit(foreignVisit, otherProfile, "CLOSED"); visit(openVisit, profile, "OPEN"); visit(unlinkedVisit, unlinked, "OPEN");
    }
    @Test void migrationSupportsKindsAndRecipientIndexes() {
        assertEquals(1, jdbc.queryForObject("select count(*) from flyway_schema_history where version = '045' and success", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from pg_indexes where schemaname=current_schema() "
                + "and indexname in ('ix_notifications_recipient_created','ix_notifications_recipient_unread')", Integer.class));
        for (NotificationRelatedKind kind : NotificationRelatedKind.values()) notification(a, kind, UUID.randomUUID(), NOW);
        assertEquals(5, inbox.unreadCount(patient).unreadCount());
    }
    @Test void listIsScopedNewestFirstAndFiltersUnread() {
        UUID old = notification(a, null, null, NOW.minusSeconds(60));
        UUID latest = notification(a, null, null, NOW.minusSeconds(10));
        UUID read = notification(a, null, null, NOW.minusSeconds(30)); inbox.markRead(patient, read);
        notification(b, null, null, NOW);
        var page = inbox.list(patient, false, 0, 20);
        assertEquals(List.of(latest, read, old), page.items().stream().map(i -> i.id()).toList());
        assertEquals(3, page.totalItems()); assertEquals(2, inbox.unreadCount(patient).unreadCount());
        var unread = inbox.list(patient, true, 0, 20);
        assertEquals(List.of(latest, old), unread.items().stream().map(i -> i.id()).toList());
        assertTrue(unread.items().stream().allMatch(i -> i.allowedActions().equals(List.of("markRead"))));
        assertEquals(1, inbox.list(other, false, 0, 20).totalItems());
        assertEquals(2, inbox.list(patient, false, 0, 2).items().size());
    }
    @Test void twoHttpPagesAreScopedCompleteAndUseDeterministicNewestOrder() throws Exception {
        UUID oldest = notification(a, null, null, NOW.minusSeconds(20));
        UUID tiedOne = notification(a, null, null, NOW.minusSeconds(10));
        UUID tiedTwo = notification(a, null, null, NOW.minusSeconds(10));
        UUID newest = notification(a, null, null, NOW);
        UUID foreign = notification(b, null, null, NOW.plusSeconds(10));
        var tied = new ArrayList<>(List.of(tiedOne, tiedTwo));
        tied.sort(Comparator.comparing(UUID::toString).reversed());
        var first = httpPage(patient, 0, 2, null);
        var second = httpPage(patient, 1, 2, null);
        assertEquals(List.of(newest, tied.get(0)), ids(first));
        assertEquals(List.of(tied.get(1), oldest), ids(second));
        for (var page : List.of(first, second)) {
            assertEquals(2, page.get("size").asInt()); assertEquals(4, page.get("totalItems").asInt());
            assertEquals(2, page.get("totalPages").asInt()); assertEquals(2, page.get("items").size());
            assertFalse(ids(page).contains(foreign));
        }
        assertEquals(0, first.get("page").asInt()); assertEquals(1, second.get("page").asInt());
        assertTrue(Collections.disjoint(ids(first), ids(second)));
        var combined = new HashSet<>(ids(first)); combined.addAll(ids(second));
        assertEquals(Set.of(oldest, tiedOne, tiedTwo, newest), combined);
        assertEquals(ids(first), ids(httpPage(patient, 0, 2, null)));
    }
    @Test void readAllMixedDatasetPreservesPreReadRowsAndOtherRecipient() throws Exception {
        var unread = List.of(notification(a, null, null, NOW), notification(a, null, null, NOW), notification(a, null, null, NOW));
        UUID readOne = notification(a, null, null, NOW.minusSeconds(60));
        UUID readTwo = notification(a, null, null, NOW.minusSeconds(120));
        jdbc.update("update notifications set read_at=? where id=?", Timestamp.from(NOW.minusSeconds(30)), readOne);
        jdbc.update("update notifications set read_at=? where id=?", Timestamp.from(NOW.minusSeconds(90)), readTwo);
        var firstTimestamp = jdbc.queryForObject("select read_at from notifications where id=?", Timestamp.class, readOne);
        var secondTimestamp = jdbc.queryForObject("select read_at from notifications where id=?", Timestamp.class, readTwo);
        notification(b, null, null, NOW); notification(b, null, null, NOW);
        mvc.perform(post(base() + "/read-all").with(actor(patient)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(3));
        for (UUID id : unread) assertEquals(Timestamp.from(NOW), jdbc.queryForObject("select read_at from notifications where id=?", Timestamp.class, id));
        assertEquals(5, jdbc.queryForObject("select count(*) from notifications where recipient_account_id=? and read_at is not null", Integer.class, a));
        assertEquals(firstTimestamp, jdbc.queryForObject("select read_at from notifications where id=?", Timestamp.class, readOne));
        assertEquals(secondTimestamp, jdbc.queryForObject("select read_at from notifications where id=?", Timestamp.class, readTwo));
        assertEquals(2, jdbc.queryForObject("select count(*) from notifications where recipient_account_id=? and read_at is null", Integer.class, b));
        mvc.perform(post(base() + "/read-all").with(actor(patient)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(0));
        assertEquals(firstTimestamp, jdbc.queryForObject("select read_at from notifications where id=?", Timestamp.class, readOne));
        assertEquals(secondTimestamp, jdbc.queryForObject("select read_at from notifications where id=?", Timestamp.class, readTwo));
        assertEquals(2, jdbc.queryForObject("select count(*) from notifications where recipient_account_id=? and read_at is null", Integer.class, b));
    }
    @Test void unreadFalseAndOmittedReturnSameMixedDataset() throws Exception {
        UUID unreadOne = notification(a, null, null, NOW);
        UUID unreadTwo = notification(a, null, null, NOW.minusSeconds(10));
        UUID read = notification(a, null, null, NOW.minusSeconds(20)); inbox.markRead(patient, read);
        notification(b, null, null, NOW);
        var omitted = httpPage(patient, 0, 20, null);
        assertEquals(omitted, httpPage(patient, 0, 20, false));
        assertEquals(List.of(unreadOne, unreadTwo, read), ids(omitted));
        assertEquals(3, omitted.get("totalItems").asInt());
        assertTrue(omitted.get("items").get(2).get("read").asBoolean());
        var unread = httpPage(patient, 0, 20, true);
        assertEquals(List.of(unreadOne, unreadTwo), ids(unread));
        assertEquals(2, unread.get("totalItems").asInt());
        for (var item : unread.get("items")) assertFalse(item.get("read").asBoolean());
        mvc.perform(get(base() + "/unread-count").with(actor(patient)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(2));
    }
    @Test void httpProjectionHasExactFieldsAndSerializesUnavailableReviewWithoutRecordData() throws Exception {
        UUID own = notification(a, NotificationRelatedKind.VISIT, visit, NOW);
        UUID unauthorized = notification(a, NotificationRelatedKind.VISIT, foreignVisit, NOW.minusSeconds(1));
        UUID missingRecord = UUID.randomUUID();
        UUID missing = notification(a, NotificationRelatedKind.VISIT, missingRecord, NOW.minusSeconds(2));
        UUID reviewRecord = UUID.randomUUID();
        UUID review = notification(a, NotificationRelatedKind.REVIEW, reviewRecord, NOW.minusSeconds(3));
        UUID plainRead = notification(a, null, null, NOW.minusSeconds(4)); inbox.markRead(patient, plainRead);
        var page = httpPage(patient, 0, 20, null);
        assertEquals(Set.of(own, unauthorized, missing, review, plainRead), new HashSet<>(ids(page)));
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        for (var item : page.get("items")) {
            UUID id = UUID.fromString(item.get("id").asText());
            assertEquals(Set.of("id", "title", "content", "createdAt", "read", "relatedRecord", "allowedActions"), item.propertyNames());
            assertTrue(item.get("createdAt").asText().endsWith("+07:00"));
            assertEquals(id.equals(plainRead), item.get("read").asBoolean());
            assertEquals(mapper.valueToTree(id.equals(plainRead) ? List.of() : List.of("markRead")), item.get("allowedActions"));
            var related = item.get("relatedRecord");
            if (id.equals(plainRead)) { assertTrue(related.isNull()); continue; }
            assertEquals(Set.of("kind", "id", "available"), related.propertyNames());
            assertEquals(id.equals(own), related.get("available").asBoolean());
            assertEquals(id.equals(review) ? "REVIEW" : "VISIT", related.get("kind").asText());
            UUID expectedRecord = id.equals(own) ? visit : id.equals(unauthorized) ? foreignVisit : id.equals(missing) ? missingRecord : reviewRecord;
            assertEquals(expectedRecord.toString(), related.get("id").asText());
        }
    }
    private tools.jackson.databind.JsonNode httpPage(AuthenticatedAccount current, int page, int size, Boolean unread) throws Exception {
        var request = get(base()).with(actor(current)).param("page", Integer.toString(page)).param("size", Integer.toString(size));
        if (unread != null) request.param("unread", unread.toString());
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(response);
    }
    private List<UUID> ids(tools.jackson.databind.JsonNode page) {
        var ids = new ArrayList<UUID>();
        for (var item : page.get("items")) ids.add(UUID.fromString(item.get("id").asText()));
        return ids;
    }
    @Test void markReadPreservesTimestampAndDoesNotUpdateRowTwice() throws Exception {
        UUID id = notification(a, null, null, NOW.minusSeconds(30));
        mvc.perform(post(base() + "/" + id + "/read").with(actor(patient)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true)).andExpect(jsonPath("$.allowedActions").isEmpty());
        var first = jdbc.queryForMap("select read_at, ctid::text as tuple from notifications where id=?", id);
        mvc.perform(post(base() + "/" + id + "/read").with(actor(patient)).with(csrf())).andExpect(status().isOk());
        assertEquals(first, jdbc.queryForMap("select read_at, ctid::text as tuple from notifications where id=?", id));
        assertEquals(0, inbox.unreadCount(patient).unreadCount());
    }
    @Test void absentForeignAndAdminCrossAccountReadsAre404() throws Exception {
        UUID foreign = notification(b, null, null, NOW);
        for (UUID id : List.of(foreign, UUID.randomUUID()))
            mvc.perform(post(base() + "/" + id + "/read").with(actor(patient)).with(csrf()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-50"));
        mvc.perform(post(base() + "/" + foreign + "/read").with(actor(administrator)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MSG-50"));
        assertNull(jdbc.queryForObject("select read_at from notifications where id=?", Timestamp.class, foreign));
    }
    @Test void readAllOnlyUpdatesCurrentRecipientAndReturnsExactCount() throws Exception {
        notification(a, null, null, NOW); notification(a, null, null, NOW); notification(b, null, null, NOW);
        mvc.perform(post(base() + "/read-all").with(actor(patient)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));
        assertEquals(0, inbox.readAll(patient).updatedCount()); assertEquals(1, inbox.unreadCount(other).unreadCount());
        assertTrue(inbox.list(patient, false, 0, 20).items().stream().allMatch(i -> i.read() && i.allowedActions().isEmpty()));
    }
    @Test void forgedQueryCannotListAnotherAccountsNotifications() throws Exception {
        notification(a, null, null, NOW); notification(b, null, null, NOW); notification(b, null, null, NOW);
        mvc.perform(get(base()).with(actor(patient)).param("accountId", b.toString()).param("recipientAccountId", b.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get(base() + "/unread-count").with(actor(patient))).andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));
    }
    @Test void relatedVisitAvailabilityIsDynamicAndDoesNotHideNotification() {
        UUID own = notification(a, NotificationRelatedKind.VISIT, visit, NOW);
        notification(a, NotificationRelatedKind.VISIT, foreignVisit, NOW);
        notification(a, NotificationRelatedKind.VISIT, UUID.randomUUID(), NOW);
        var page = inbox.list(patient, false, 0, 20);
        assertEquals(3, page.totalItems());
        assertTrue(page.items().stream().filter(i -> i.id().equals(own)).findFirst().orElseThrow().relatedRecord().available());
        assertEquals(1, page.items().stream().filter(i -> i.relatedRecord().available()).count());
        jdbc.update("update patient_profiles set account_id = null where id=?", profile);
        assertTrue(inbox.list(patient, false, 0, 20).items().stream().noneMatch(i -> i.relatedRecord().available()));
    }
    @Test void bookingAndReassignmentPublishAppointmentNotifications() {
        jdbc.update("update staff_profiles set display_name='Bác sĩ ban đầu' where id=?", doctor);
        jdbc.update("update staff_profiles set display_name='Bác sĩ thay thế' where id=?", replacement);
        var created = book(profile, CreateAppointmentCommand.Actor.PATIENT);
        assertMessage(NotificationMessages.APPOINTMENT_BOOKED, a, created.id(), "APPOINTMENT", "03/11/2026 lúc 10:00", "Bác sĩ ban đầu");
        assertTrue(inbox.list(patient, false, 0, 20).items().getFirst().relatedRecord().available());
        notification(b, NotificationRelatedKind.APPOINTMENT, created.id(), NOW);
        assertFalse(inbox.list(other, false, 0, 20).items().getFirst().relatedRecord().available());
        reassign.reassign(created.id(), new ReassignAppointmentCommand(receptionist, replacement, "Doctor unavailable", "notification-test"));
        assertMessage(NotificationMessages.APPOINTMENT_DOCTOR_CHANGED, a, created.id(), "APPOINTMENT", "03/11/2026 lúc 10:00", "Bác sĩ thay thế");
        assertEquals(0, count("APPOINTMENT_DOCTOR_REASSIGNED", a, created.id()));
        assertEquals(2, jdbc.queryForObject("select count(*) from notifications where recipient_account_id=? and related_id=?", Integer.class, a, created.id()));
        assertEquals(replacement, jdbc.queryForObject("select doctor_profile_id from appointments where id=?", UUID.class, created.id()));
    }
    @Test void closeVisitPublishesInvitationOnlyForLinkedPatient() {
        jdbc.update("insert into visit_assignments(id,visit_id,doctor_profile_id,starts_at,ends_at,status) values (?,?,?,?,?,'COMPLETED')",
                UUID.randomUUID(), openVisit, doctor, Timestamp.from(NOW.minusSeconds(600)), Timestamp.from(NOW));
        for (UUID id : List.of(openVisit, unlinkedVisit)) {
            jdbc.update("insert into clinical_examinations(id,visit_id,status,created_at,updated_at,version) values (?,?,'COMPLETED',?,?,0)",
                    UUID.randomUUID(), id, Timestamp.from(NOW), Timestamp.from(NOW));
        }
        lifecycle.close(openVisit, receptionist, AccountRole.RECEPTIONIST, "notification-test");
        assertEquals("CLOSED", jdbc.queryForObject("select status from visits where id=?", String.class, openVisit));
        assertMessage(NotificationMessages.VISIT_CLOSED_REVIEW_INVITE, a, openVisit, "VISIT", "01/11/2026 lúc 11:00", "Notification test doctor");
        assertEquals(0, count("VISIT_CLOSED", a, openVisit));
        assertThrows(fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException.class,
                () -> lifecycle.close(openVisit, receptionist, AccountRole.RECEPTIONIST, "notification-test"));
        assertEquals(1, count(NotificationMessages.VISIT_CLOSED_REVIEW_INVITE, a, openVisit));
        lifecycle.close(unlinkedVisit, receptionist, AccountRole.RECEPTIONIST, "notification-test");
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where related_id=?", Integer.class, unlinkedVisit));
    }
    @Test void bookingForWalkInProfileDoesNotInventRecipient() {
        var created = book(unlinked, CreateAppointmentCommand.Actor.RECEPTIONIST);
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where related_id=?", Integer.class, created.id()));
    }
    @Test void reviewPublishesOnlyToActiveAdminsAndResolverChecksRoleAndExistence() {
        UUID secondAdmin = UUID.randomUUID(); account(secondAdmin, "ADMIN", "ACTIVE");
        jdbc.update("insert into visit_assignments(id,visit_id,doctor_profile_id,starts_at,ends_at,status) values (?,?,?,?,?,'COMPLETED')",
                UUID.randomUUID(), visit, doctor, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW));
        var created = reviews.submit(a, new SubmitReviewRequest(visit, 5, "A valid clinic review"));
        assertMessage(NotificationMessages.REVIEW_SUBMITTED, admin, created.id(), "REVIEW", "01/11/2026 lúc 10:59", "Notification test doctor");
        assertMessage(NotificationMessages.REVIEW_SUBMITTED, secondAdmin, created.id(), "REVIEW", "01/11/2026 lúc 10:59", "Notification test doctor");
        assertTrue(jdbc.queryForObject("select content from notifications where recipient_account_id=? and related_id=?", String.class, admin, created.id()).contains("5/5"));
        assertEquals(0, count("REVIEW_SUBMITTED", owner, created.id()));
        assertEquals(0, count("REVIEW_SUBMITTED", disabledAdmin, created.id()));
        var item = inbox.list(administrator, false, 0, 100).items().stream()
                .filter(i -> i.relatedRecord() != null && i.relatedRecord().id().equals(created.id())).findFirst().orElseThrow();
        assertEquals(NotificationRelatedKind.REVIEW, item.relatedRecord().kind()); assertTrue(item.relatedRecord().available());
        var changedRole = new AuthenticatedAccount(admin, AccountRole.OWNER, UUID.randomUUID());
        assertFalse(inbox.list(changedRole, false, 0, 100).items().getFirst().relatedRecord().available());
        UUID missing = notification(admin, NotificationRelatedKind.REVIEW, UUID.randomUUID(), NOW);
        assertFalse(inbox.list(administrator, false, 0, 100).items().stream().filter(i -> i.id().equals(missing)).findFirst().orElseThrow().relatedRecord().available());
    }
    @Test void receptionistCancellationPublishesOnceAndPatientSelfCancellationDoesNot() {
        var created = book(profile, CreateAppointmentCommand.Actor.PATIENT);
        cancellations.cancel(created.id(), new CancelAppointmentCommand(receptionist, CancelAppointmentCommand.Actor.RECEPTIONIST,
                "Clinic cancellation", "notification-test"));
        assertEquals("CANCELLED", jdbc.queryForObject("select status from appointments where id=?", String.class, created.id()));
        assertMessage(NotificationMessages.APPOINTMENT_CANCELLED, a, created.id(), "APPOINTMENT", "03/11/2026 lúc 10:00", "Notification test doctor");
        var self = book(profile, CreateAppointmentCommand.Actor.PATIENT);
        cancellations.cancel(self.id(), new CancelAppointmentCommand(a, CancelAppointmentCommand.Actor.PATIENT, "Self cancellation", "notification-test"));
        assertEquals("CANCELLED", jdbc.queryForObject("select status from appointments where id=?", String.class, self.id()));
        assertEquals(0, count(NotificationMessages.APPOINTMENT_CANCELLED, a, self.id()));
        var noAccount = book(unlinked, CreateAppointmentCommand.Actor.RECEPTIONIST);
        cancellations.cancel(noAccount.id(), new CancelAppointmentCommand(receptionist, CancelAppointmentCommand.Actor.RECEPTIONIST,
                "Clinic cancellation", "notification-test"));
        assertEquals("CANCELLED", jdbc.queryForObject("select status from appointments where id=?", String.class, noAccount.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where related_id=?", Integer.class, noAccount.id()));
    }
    @Test void reschedulingPublishesNewTimeAndCurrentDoctorOnceAndSkipsUnlinkedProfiles() {
        var created = book(profile, CreateAppointmentCommand.Actor.PATIENT);
        var newTime = SLOT.plusSeconds(1800);
        reschedules.reschedule(created.id(), new RescheduleAppointmentCommand(receptionist, RescheduleAppointmentCommand.Actor.RECEPTIONIST,
                newTime.atOffset(ZoneOffset.UTC), "Clinic reschedule", "notification-test"));
        assertEquals(newTime, jdbc.queryForObject("select start_time from appointments where id=?", Timestamp.class, created.id()).toInstant());
        assertMessage(NotificationMessages.APPOINTMENT_RESCHEDULED, a, created.id(), "APPOINTMENT", "03/11/2026 lúc 10:30", "Notification test doctor");
        var noAccount = book(unlinked, CreateAppointmentCommand.Actor.RECEPTIONIST);
        reschedules.reschedule(noAccount.id(), new RescheduleAppointmentCommand(receptionist, RescheduleAppointmentCommand.Actor.RECEPTIONIST,
                SLOT.plusSeconds(3600).atOffset(ZoneOffset.UTC), "Clinic reschedule", "notification-test"));
        assertEquals(SLOT.plusSeconds(3600), jdbc.queryForObject("select start_time from appointments where id=?", Timestamp.class, noAccount.id()).toInstant());
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where related_id=?", Integer.class, noAccount.id()));
    }
    @ParameterizedTest @ValueSource(strings = {"CANCEL", "RESCHEDULE"})
    void newProducerSourceRollbackAlsoRollsBackNotifications(String operation) {
        endFixtures();
        assertThrows(RollbackProbe.class, () -> new TransactionTemplate(transactions).execute(status -> {
            fixtures(); var created = book(profile, CreateAppointmentCommand.Actor.PATIENT);
            changeAppointment(operation, created.id());
            assertEquals(1, count(operation.equals("CANCEL") ? NotificationMessages.APPOINTMENT_CANCELLED
                    : NotificationMessages.APPOINTMENT_RESCHEDULED, a, created.id()));
            throw new RollbackProbe();
        }));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointments where created_by_account_id=?", Integer.class, a));
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_account_id=?", Integer.class, a));
    }
    @ParameterizedTest @ValueSource(strings = {"CANCEL", "RESCHEDULE"})
    void newProducerNotificationFailureRollsBackSourceAndAudit(String operation) {
        endFixtures();
        var failure = assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            fixtures(); var created = book(profile, CreateAppointmentCommand.Actor.PATIENT);
            jdbc.execute("create function notification_test_reject_change() returns trigger language plpgsql as $$ begin "
                    + "raise exception 'change notification failure'; end; $$");
            String type = operation.equals("CANCEL") ? NotificationMessages.APPOINTMENT_CANCELLED : NotificationMessages.APPOINTMENT_RESCHEDULED;
            jdbc.execute("create trigger notification_test_reject_change before insert on notifications for each row when (new.type='"
                    + type + "') execute function notification_test_reject_change()");
            changeAppointment(operation, created.id()); return null;
        }));
        assertTrue(causeContains(failure, "change notification failure"));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointments where created_by_account_id=?", Integer.class, a));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and actor_account_id=?", Integer.class, receptionist));
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_account_id=?", Integer.class, a));
    }
    private void changeAppointment(String operation, UUID id) {
        if (operation.equals("CANCEL")) cancellations.cancel(id, new CancelAppointmentCommand(receptionist,
                CancelAppointmentCommand.Actor.RECEPTIONIST, "Clinic cancellation", "notification-test"));
        else reschedules.reschedule(id, new RescheduleAppointmentCommand(receptionist, RescheduleAppointmentCommand.Actor.RECEPTIONIST,
                SLOT.plusSeconds(1800).atOffset(ZoneOffset.UTC), "Clinic reschedule", "notification-test"));
    }
    private void assertMessage(String type, UUID recipient, UUID related, String kind, String time, String doctorName) {
        var rows = jdbc.queryForList("select related_kind, title, content from notifications where type=? and recipient_account_id=? and related_id=?",
                type, recipient, related);
        assertEquals(1, rows.size()); assertEquals(kind, rows.getFirst().get("related_kind"));
        assertFalse(((String) rows.getFirst().get("title")).isBlank());
        String content = (String) rows.getFirst().get("content");
        assertTrue(content.contains(time), content); assertTrue(content.contains(doctorName), content);
    }
    @Test void businessRollbackAlsoRollsBackNotifications() {
        endFixtures();
        assertThrows(RollbackProbe.class, () -> new TransactionTemplate(transactions).execute(status -> {
            fixtures(); var created = book(profile, CreateAppointmentCommand.Actor.PATIENT);
            assertEquals(1, count("APPOINTMENT_BOOKED", a, created.id()));
            throw new RollbackProbe();
        }));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointments where created_by_account_id=?", Integer.class, a));
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_account_id=?", Integer.class, a));
    }
    @Test void notificationInsertFailureRollsBackReviewAndHistory() {
        endFixtures();
        var failure = assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            fixtures();
            jdbc.execute("create function notification_test_reject_insert() returns trigger language plpgsql as $$ begin "
                    + "raise exception 'notification test failure'; end; $$");
            jdbc.execute("create trigger notification_test_reject_insert before insert on notifications for each row execute function notification_test_reject_insert()");
            reviews.submit(a, new SubmitReviewRequest(visit, 5, "A valid clinic review"));
            return null;
        }));
        assertTrue(causeContains(failure, "notification test failure"));
        assertEquals(0, jdbc.queryForObject("select count(*) from patient_reviews where author_account_id=?", Integer.class, a));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'patient_review_moderation_history' and actor_account_id=?", Integer.class, a));
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_account_id=?", Integer.class, admin));
    }
    @Test void publisherRequiresAnExistingBusinessTransaction() {
        endFixtures();
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> publisher.publish(a, new NotificationMessage("TEST", "Title", "Content", null, null)));
    }
    @Test void unavailableRelatedRecordDoesNotMarkReadTransactionRollbackOnly() {
        endFixtures();
        new TransactionTemplate(transactions).execute(status -> {
            fixtures();
            UUID id = notification(a, NotificationRelatedKind.VISIT, foreignVisit, NOW);
            var result = inbox.markRead(patient, id);
            assertTrue(result.read()); assertFalse(result.relatedRecord().available());
            assertFalse(status.isRollbackOnly(), "An inaccessible related record must not poison the inbox transaction");
            status.setRollbackOnly(); return null;
        });
    }
    @Test void notificationInsertFailureRollsBackAppointmentAndAudit() {
        endFixtures();
        var failure = assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            fixtures();
            jdbc.execute("create function notification_test_reject_booking() returns trigger language plpgsql as $$ begin "
                    + "raise exception 'booking notification test failure'; end; $$");
            jdbc.execute("create trigger notification_test_reject_booking before insert on notifications for each row execute function notification_test_reject_booking()");
            book(profile, CreateAppointmentCommand.Actor.PATIENT);
            return null;
        }));
        assertTrue(causeContains(failure, "booking notification test failure"));
        assertEquals(0, jdbc.queryForObject("select count(*) from appointments where created_by_account_id=?", Integer.class, a));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'appointment_audits' and actor_account_id=?", Integer.class, a));
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_account_id=?", Integer.class, a));
    }
    @Test void cancelDoesNotPublishReviewInvitation() {
        lifecycle.cancel(openVisit, receptionist, AccountRole.RECEPTIONIST, "notification-test");
        assertEquals(0, count(NotificationMessages.VISIT_CLOSED_REVIEW_INVITE, a, openVisit));
    }
    @Test void swaggerDocumentsAll14OperationsAndPrivatePublicSchemas() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var document = tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        var operations = Map.ofEntries(
                Map.entry("/api/v1/me/reviews/eligible-visits", "get"), Map.entry("/api/v1/me/reviews", "get"),
                Map.entry("/api/v1/reviews", "post"), Map.entry("/api/v1/public/reviews", "get"),
                Map.entry("/api/v1/admin/reviews", "get"), Map.entry("/api/v1/admin/reviews/{id}", "get"),
                Map.entry("/api/v1/admin/reviews/{id}/approve", "post"), Map.entry("/api/v1/admin/reviews/{id}/hide", "post"),
                Map.entry("/api/v1/admin/reviews/{id}/unhide", "post"), Map.entry("/api/v1/admin/reviews/{id}/reply", "put"),
                Map.entry("/api/v1/me/notifications", "get"), Map.entry("/api/v1/me/notifications/unread-count", "get"),
                Map.entry("/api/v1/me/notifications/{id}/read", "post"), Map.entry("/api/v1/me/notifications/read-all", "post"));
        var operationIds = new HashSet<String>();
        var resolvedReferences = new HashSet<String>();
        for (var entry : operations.entrySet()) {
            var operation = document.get("paths").get(entry.getKey()).get(entry.getValue());
            assertNotNull(operation, entry.getKey());
            assertTrue(operationIds.add(operation.path("operationId").asText()), "Duplicate operationId: " + entry.getKey());
            assertFalse(operation.path("summary").asText().isBlank(), entry.getKey());
            assertFalse(operation.path("description").asText().isBlank(), entry.getKey());
            String success = entry.getKey().equals("/api/v1/reviews") ? "201" : "200";
            assertTrue(operation.get("responses").has(success), entry.getKey());
            assertEquals("#/components/schemas/ErrorResponse", operation.get("responses").get("500")
                    .get("content").get("application/json").get("schema").get("$ref").asText(), entry.getKey());
            if (entry.getKey().equals("/api/v1/public/reviews"))
                assertTrue(operation.path("security").isMissingNode() || operation.path("security").isEmpty());
            else assertFalse(operation.path("security").isEmpty(), entry.getKey());
            var responses = operation.get("responses");
            for (String status : responses.propertyNames()) {
                var response = responses.get(status);
                assertEquals(Set.of("application/json"), response.get("content").propertyNames(), entry.getKey() + " " + status);
                assertTrue(response.get("content").get("application/json").has("schema"), entry.getKey() + " " + status);
                if (status.startsWith("4") || status.startsWith("5"))
                    assertEquals("#/components/schemas/ErrorResponse",
                            response.get("content").get("application/json").get("schema").get("$ref").asText());
            }
            assertDocumentedMessage(responses, "500", "MSG-04");
            String path = entry.getKey();
            if (!path.equals("/api/v1/public/reviews")) assertTrue(responses.has("401"), path);
            if (path.startsWith("/api/v1/admin/reviews")) assertTrue(responses.has("403"), path);
            if (path.startsWith("/api/v1/me/reviews") || path.equals("/api/v1/reviews"))
                assertDocumentedMessage(responses, "403", "MSG-56");
            if (path.equals("/api/v1/admin/reviews") || path.equals("/api/v1/public/reviews") || path.equals("/api/v1/me/notifications"))
                assertDocumentedMessage(responses, "400", "MSG-54");
            if (path.equals("/api/v1/reviews") || path.endsWith("/reply")) assertDocumentedMessage(responses, "400", "MSG-57");
            if (path.startsWith("/api/v1/admin/reviews/{id}") || path.equals("/api/v1/me/notifications/{id}/read"))
                assertDocumentedMessage(responses, "404", "MSG-50");
            if (path.equals("/api/v1/reviews") || path.matches("/api/v1/admin/reviews/\\{id}/(approve|hide|unhide|reply)"))
                assertDocumentedMessage(responses, "409", "MSG-56");
            assertOpenApiReferencesResolve(document, operation, resolvedReferences);
            for (var parameter : operation.path("parameters"))
                assertFalse(Set.of("actor", "accountId", "recipientAccountId").contains(parameter.path("name").asText()));
        }
        var schemas = document.get("components").get("schemas");
        var publicFields = schemas.get("PublicReview").get("properties");
        assertEquals(5, publicFields.size());
        for (String name : List.of("id", "rating", "text", "createdAt", "clinicReply")) assertTrue(publicFields.has(name));
        var notificationFields = schemas.get("NotificationItem").get("properties");
        assertEquals(7, notificationFields.size());
        for (String name : List.of("id", "title", "content", "createdAt", "read", "relatedRecord", "allowedActions")) assertTrue(notificationFields.has(name));
        var errorFields = schemas.get("ErrorResponse").get("properties");
        assertTrue(errorFields.has("error")); assertTrue(errorFields.has("fieldErrors")); assertFalse(errorFields.has("code"));
        assertTrue(schemas.get("ReviewListItem").get("properties").has("content"));
        assertFalse(schemas.get("ReviewListItem").get("properties").has("text"));
        assertEquals(14, operationIds.size());
        assertReviewWriteExamples(document);
        for (String code : List.of("MSG-57", "MSG-54", "MSG-50", "MSG-56", "MSG-04")) {
            var example = document.get("components").get("examples").get(code).get("value");
            assertEquals(code, example.get("error").get("code").asText()); assertFalse(example.has("code"));
            assertFalse(example.has("stacktrace")); assertFalse(example.get("error").has("stacktrace"));
            if (code.equals("MSG-57")) {
                assertEquals("text", example.get("fieldErrors").get(0).get("field").asText());
                assertEquals(code, example.get("fieldErrors").get(0).get("code").asText());
            } else assertFalse(example.has("fieldErrors"));
        }
        var appointmentCreated = document.get("paths").get("/api/v1/appointments").get("post").get("responses").get("201");
        assertTrue(appointmentCreated.get("headers").has("Location"));
        assertTrue(appointmentCreated.get("content").has("application/json"));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/uc59-uc63-openapi-audit.json"), document.toPrettyString());
    }
    private void assertDocumentedMessage(tools.jackson.databind.JsonNode responses, String status, String code) {
        var response = responses.get(status); assertNotNull(response, status + " " + code);
        assertTrue(response.get("description").asText().contains(code));
        assertEquals("#/components/examples/" + code,
                response.get("content").get("application/json").get("examples").get(code).get("$ref").asText());
    }
    private void assertOpenApiReferencesResolve(tools.jackson.databind.JsonNode document, tools.jackson.databind.JsonNode node, Set<String> visited) {
        if (node.has("$ref")) {
            String reference = node.get("$ref").asText(); assertTrue(reference.startsWith("#/"), reference);
            var target = document.at(reference.substring(1)); assertFalse(target.isMissingNode(), reference);
            if (visited.add(reference)) assertOpenApiReferencesResolve(document, target, visited);
        }
        for (var child : node) assertOpenApiReferencesResolve(document, child, visited);
    }
    private void assertReviewWriteExamples(tools.jackson.databind.JsonNode document) {
        var submit = document.get("paths").get("/api/v1/reviews").get("post");
        var request = submit.get("requestBody").get("content").get("application/json").get("example");
        assertEquals(Set.of("visitId", "rating", "text"), request.propertyNames());
        assertEquals("7b6d9d88-bf52-4d9d-8cb2-86cf44e16a18", request.get("visitId").asText());
        assertEquals(5, request.get("rating").asInt());
        assertEquals("Dịch vụ tốt, bác sĩ tư vấn rõ ràng.", request.get("text").asText());
        var created = submit.get("responses").get("201").get("content").get("application/json").get("example");
        assertEquals(Set.of("id", "visitId", "visitDate", "rating", "text", "status", "clinicReply", "createdAt", "patient"), created.propertyNames());
        assertEquals(request.get("visitId"), created.get("visitId")); assertEquals(request.get("text"), created.get("text"));
        assertEquals("PENDING", created.get("status").asText()); assertTrue(created.get("clinicReply").isNull());
        assertEquals("SELF", created.get("patient").get("relationship").asText());
        assertEquals("2026-11-01", created.get("visitDate").asText());
        assertTrue(created.get("createdAt").asText().endsWith("+07:00"));
        var replyOperation = document.get("paths").get("/api/v1/admin/reviews/{id}/reply").get("put");
        var replyRequest = replyOperation.get("requestBody").get("content").get("application/json").get("example");
        assertEquals(Set.of("text"), replyRequest.propertyNames());
        assertEquals("Cảm ơn bạn đã gửi đánh giá cho phòng khám.", replyRequest.get("text").asText());
        var replied = replyOperation.get("responses").get("200").get("content").get("application/json").get("example");
        assertEquals(Set.of("id", "submittedAt", "patientName", "rating", "content", "status", "replied", "visit", "allowedActions", "reply"), replied.propertyNames());
        assertEquals(request.get("text"), replied.get("content")); assertFalse(replied.has("text"));
        assertTrue(replied.get("replied").asBoolean()); assertEquals("PUBLISHED", replied.get("status").asText());
        assertEquals(replyRequest.get("text"), replied.get("reply").get("text"));
        assertTrue(replied.get("reply").get("repliedAt").asText().endsWith("+07:00"));
        assertEquals("hide", replied.get("allowedActions").get(0).asText());
        assertEquals("editReply", replied.get("allowedActions").get(1).asText());
        assertEquals(2, replied.get("allowedActions").size());
    }
    private boolean causeContains(Throwable error, String text) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) if (cause.getMessage() != null && cause.getMessage().contains(text)) return true;
        return false;
    }
    private void endFixtures() { TestTransaction.flagForRollback(); TestTransaction.end(); }
    private static class RollbackProbe extends RuntimeException { }
    private fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment book(UUID target, CreateAppointmentCommand.Actor actor) {
        return booking.create(new CreateAppointmentCommand(actor == CreateAppointmentCommand.Actor.PATIENT ? a : receptionist,
                actor, target, serviceId, doctor, SLOT.atOffset(ZoneOffset.UTC), "Clinic appointment", "notification-test"));
    }
    private int count(String type, UUID recipient, UUID related) {
        return jdbc.queryForObject("select count(*) from notifications where type=? and recipient_account_id=? and related_id=?", Integer.class, type, recipient, related);
    }
    private UUID notification(UUID recipient, NotificationRelatedKind kind, UUID related, Instant created) {
        return repository.saveAndFlush(NotificationJpaEntity.create(recipient, new NotificationMessage("TEST", "Title", "Content", kind, related), created)).getId();
    }
    private void account(UUID id, String role, String status) {
        jdbc.update("insert into accounts(id, normalized_email, account_type, status, password_hash, created_at, updated_at) values (?,?,?,?, 'test',now(),now())",
                id, "notification-" + id + "@example.test", role, status);
    }
    private void profile(UUID id, UUID account) {
        jdbc.update("insert into patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender) "
                + "values (?,?,'Notification test patient',?,'PATIENT',date '1990-01-01','OTHER')", id, account,
                "+849" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L)));
    }
    private void doctor(UUID id, UUID account) {
        jdbc.update("insert into staff_profiles(is_doctor, id,account_id,display_name,publicly_visible,accepting_new_bookings,in_active_directory) values (true, ?,?,'Notification test doctor',true,true,true)", id, account);
        jdbc.update("insert into doctor_services(doctor_profile_id,service_id,active) values (?,?,true)", id, serviceId);
        jdbc.update("insert into work_shifts(id,staff_profile_id,starts_at,ends_at) values (?,?,?,?)", UUID.randomUUID(), id,
                Timestamp.from(Instant.parse("2026-11-03T02:00:00Z")), Timestamp.from(Instant.parse("2026-11-03T05:00:00Z")));
    }
    private void visit(UUID id, UUID target, String status) {
        jdbc.update("insert into visits(id,patient_profile_id,source,arrived_at,checked_in_at,created_by_account_id,status) values (?,?,'WALK_IN',?,?,?,?)",
                id, target, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(60)), receptionist, status);
    }
    private String base() { return "/api/v1/me/notifications"; }
    private RequestPostProcessor actor(AuthenticatedAccount current) {
        return authentication(new UsernamePasswordAuthenticationToken(current, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + current.accountRole().name()))));
    }
    @TestConfiguration static class FixedClock {
        @Bean @Primary Clock notificationClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
