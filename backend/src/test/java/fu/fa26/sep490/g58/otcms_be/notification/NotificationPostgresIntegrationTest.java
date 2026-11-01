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
        var created = book(profile, CreateAppointmentCommand.Actor.PATIENT);
        assertEquals(1, count("APPOINTMENT_BOOKED", a, created.id()));
        assertTrue(inbox.list(patient, false, 0, 20).items().getFirst().relatedRecord().available());
        notification(b, NotificationRelatedKind.APPOINTMENT, created.id(), NOW);
        assertFalse(inbox.list(other, false, 0, 20).items().getFirst().relatedRecord().available());
        reassign.reassign(created.id(), new ReassignAppointmentCommand(receptionist, replacement, "Doctor unavailable", "notification-test"));
        assertEquals(1, count("APPOINTMENT_DOCTOR_REASSIGNED", a, created.id()));
        assertEquals(replacement, jdbc.queryForObject("select doctor_profile_id from appointments where id=?", UUID.class, created.id()));
    }
    @Test void closeVisitPublishesInvitationOnlyForLinkedPatient() {
        lifecycle.close(openVisit, doctorAccount, AccountRole.DOCTOR, "notification-test");
        assertEquals("CLOSED", jdbc.queryForObject("select status from visits where id=?", String.class, openVisit));
        assertEquals(1, count("VISIT_CLOSED", a, openVisit));
        lifecycle.close(unlinkedVisit, doctorAccount, AccountRole.DOCTOR, "notification-test");
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where related_id=?", Integer.class, unlinkedVisit));
    }
    @Test void bookingForWalkInProfileDoesNotInventRecipient() {
        var created = book(unlinked, CreateAppointmentCommand.Actor.RECEPTIONIST);
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where related_id=?", Integer.class, created.id()));
    }
    @Test void reviewPublishesOnlyToActiveAdminsAndResolverChecksRoleAndExistence() {
        var created = reviews.submit(a, new SubmitReviewRequest(visit, 5, "A valid clinic review"));
        assertEquals(1, count("REVIEW_SUBMITTED", admin, created.id()));
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
        assertEquals(0, jdbc.queryForObject("select count(*) from patient_review_moderation_history where actor_account_id=?", Integer.class, a));
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
        assertEquals(0, jdbc.queryForObject("select count(*) from appointment_audits where actor_account_id=?", Integer.class, a));
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where recipient_account_id=?", Integer.class, a));
    }
    @Test void cancelDoesNotPublishReviewInvitation() {
        lifecycle.cancel(openVisit, receptionist, AccountRole.RECEPTIONIST, "notification-test");
        assertEquals(0, count("VISIT_CLOSED", a, openVisit));
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
        for (var entry : operations.entrySet()) {
            var operation = document.get("paths").get(entry.getKey()).get(entry.getValue());
            assertNotNull(operation, entry.getKey());
            assertFalse(operation.path("summary").asText().isBlank(), entry.getKey());
            assertFalse(operation.path("description").asText().isBlank(), entry.getKey());
            String success = entry.getKey().equals("/api/v1/reviews") ? "201" : "200";
            assertTrue(operation.get("responses").has(success), entry.getKey());
            assertEquals("#/components/schemas/ErrorResponse", operation.get("responses").get("500")
                    .get("content").get("application/json").get("schema").get("$ref").asText(), entry.getKey());
            if (entry.getKey().equals("/api/v1/public/reviews"))
                assertTrue(operation.path("security").isMissingNode() || operation.path("security").isEmpty());
            else assertFalse(operation.path("security").isEmpty(), entry.getKey());
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
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/uc59-uc63-openapi-audit.json"), document.toPrettyString());
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
        jdbc.update("insert into doctor_profiles(id,account_id,full_name,publicly_visible,accepting_new_bookings,in_active_directory) values (?,?,'Notification test doctor',true,true,true)", id, account);
        jdbc.update("insert into doctor_services(doctor_profile_id,service_id,active) values (?,?,true)", id, serviceId);
        jdbc.update("insert into doctor_shifts(id,doctor_profile_id,starts_at,ends_at) values (?,?,?,?)", UUID.randomUUID(), id,
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
