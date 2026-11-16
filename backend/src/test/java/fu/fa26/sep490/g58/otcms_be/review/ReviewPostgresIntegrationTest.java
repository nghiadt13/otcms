package fu.fa26.sep490.g58.otcms_be.review;

import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.*;
import fu.fa26.sep490.g58.otcms_be.review.model.entity.PatientReviewJpaEntity;
import fu.fa26.sep490.g58.otcms_be.review.service.ReviewService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest @LocalPostgresTest @Transactional @AutoConfigureMockMvc
class ReviewPostgresIntegrationTest {
    // The concurrency test commits real transactions. Isolate and remove this class's entire test schema,
    // rather than bypassing the append-only history trigger to delete committed fixtures.
    private static final String TEST_SCHEMA = "review_it_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void isolatedSchema(DynamicPropertyRegistry registry) {
        String baseUrl = System.getProperty("spring.datasource.url", System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:" + System.getenv().getOrDefault("OTCMS_TEST_DB_PORT", "5432") + "/" + System.getenv().getOrDefault("OTCMS_TEST_DB_NAME", "otcms_test_db")));
        String url = baseUrl.contains("currentSchema=")
                ? baseUrl.replaceAll("currentSchema=[^&]*", "currentSchema=" + TEST_SCHEMA + ",public")
                : baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + TEST_SCHEMA + ",public";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
    }
    @AfterAll static void removeIsolatedSchema(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("drop schema " + TEST_SCHEMA + " cascade");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReviewService reviews;
    @Autowired EntityManager entityManager;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    final UUID account = UUID.randomUUID(), otherAccount = UUID.randomUUID(), admin = UUID.randomUUID();
    final UUID profile = UUID.randomUUID(), otherProfile = UUID.randomUUID();
    final UUID visit = UUID.randomUUID(), otherVisit = UUID.randomUUID(), openVisit = UUID.randomUUID();
    @BeforeEach void fixtures() {
        account(account, "PATIENT"); account(otherAccount, "PATIENT"); account(admin, "ADMIN");
        profile(profile, account); profile(otherProfile, otherAccount);
        visit(visit, profile, "CLOSED"); visit(otherVisit, otherProfile, "CLOSED"); visit(openVisit, profile, "OPEN");
    }
    @Test void concurrentAdminsCommitOnlyWinningMutationAndHistory() throws Exception {
        endFixtureTransaction();
        UUID losingAdmin = UUID.randomUUID();
        var transaction = new TransactionTemplate(transactionManager);
        UUID id = transaction.execute(state -> {
            fixtures(); account(losingAdmin, "ADMIN");
            return reviews.submit(account, new SubmitReviewRequest(visit, 5, "Concurrent admin review")).id();
        });
        long initialVersion = version(id);
        var bothLoaded = new CountDownLatch(2);
        var winnerCommitted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var winner = executor.submit(() -> {
                try {
                    return transaction.execute(state -> {
                        var loaded = entityManager.find(PatientReviewJpaEntity.class, id);
                        assertEquals(initialVersion, loaded.getVersion());
                        bothLoaded.countDown(); await(bothLoaded);
                        return reviews.decide(id, admin, "HIDE");
                    });
                } finally { winnerCommitted.countDown(); }
            });
            var loser = executor.submit(() -> assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,
                    () -> transaction.execute(state -> {
                        var loaded = entityManager.find(PatientReviewJpaEntity.class, id);
                        assertEquals(initialVersion, loaded.getVersion());
                        bothLoaded.countDown(); await(bothLoaded); await(winnerCommitted);
                        return reviews.decide(id, losingAdmin, "APPROVE");
                    })));
            assertEquals("HIDDEN", winner.get(30, TimeUnit.SECONDS).status().name());
            var failure = loser.get(30, TimeUnit.SECONDS);
            // Map the real persistence failure through production advice, without mocking the exception or service.
            var mapped = new fu.fa26.sep490.g58.otcms_be.review.exception.ReviewApiExceptionHandler().optimistic(failure);
            assertEquals(409, mapped.getStatusCode().value());
            assertEquals("MSG-56", mapped.getBody().error().code());
            assertEquals("HIDDEN", jdbc.queryForObject("select status from patient_reviews where id = ?", String.class, id));
            assertEquals(initialVersion + 1, version(id));
            assertEquals(List.of("SUBMIT", "HIDE"), jdbc.queryForList(
                    "select action from audit_logs where source = 'patient_review_moderation_history' and entity_id = ? order by occurred_at, id", String.class, id));
            assertEquals(admin, jdbc.queryForObject("select actor_account_id from audit_logs where source = 'patient_review_moderation_history' and entity_id = ? and action = 'HIDE'", UUID.class, id));
            assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'patient_review_moderation_history' and entity_id = ? and actor_account_id = ?", Integer.class, id, losingAdmin));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(15, TimeUnit.SECONDS), "Concurrent Admin transaction timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
    @Test void hiddenReplyDisappearsEntirelyFromPublicHttp() throws Exception {
        var id = publicReviewFixture(5, "PUBLISHED", Instant.parse("2026-11-01T12:00:00Z"));
        mvc.perform(put("/api/v1/admin/reviews/" + id + "/reply").with(adminActor(admin)).with(csrf())
                        .contentType("application/json").content("{\"text\":\" Public clinic reply \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reply.text").value("Public clinic reply"));
        var before = publicPage(0, 20);
        assertEquals(List.of(id), before.items().stream().map(PublicReview::id).toList());
        assertEquals("Public clinic reply", before.items().getFirst().clinicReply());
        reviews.decide(id, admin, "HIDE");
        var after = publicPage(0, 20);
        assertTrue(after.items().stream().noneMatch(item -> item.id().equals(id)));
        assertEquals(before.summary().count() - 1, after.summary().count());
        assertEquals("Public clinic reply", jdbc.queryForObject("select reply_text from patient_reviews where id = ?", String.class, id));
    }
    @ParameterizedTest @CsvSource({"PENDING", "PUBLISHED", "HIDDEN"})
    void replyAndEditPreserveStatusAndAppendImmutableSnapshots(String state) throws Exception {
        var id = publicReviewFixture(5, state, Instant.parse("2026-11-01T12:00:00Z"));
        UUID editingAdmin = UUID.randomUUID(); account(editingAdmin, "ADMIN");
        mvc.perform(put("/api/v1/admin/reviews/" + id + "/reply").with(adminActor(admin)).with(csrf())
                        .contentType("application/json").content("{\"text\":\" First reply \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state))
                .andExpect(jsonPath("$.reply.text").value("First reply"));
        entityManager.flush();
        var original = jdbc.queryForMap("select *, before_state->>'status' as from_status, after_state->>'status' as to_status, after_state->>'replyText' as reply_text from audit_logs where source = 'patient_review_moderation_history' and entity_id = ? and action = 'REPLY'", id);
        var firstReplyTime = jdbc.queryForObject("select replied_at from patient_reviews where id = ?", java.sql.Timestamp.class, id);
        mvc.perform(put("/api/v1/admin/reviews/" + id + "/reply").with(adminActor(editingAdmin)).with(csrf())
                        .contentType("application/json").content("{\"text\":\" Updated reply \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state))
                .andExpect(jsonPath("$.reply.text").value("Updated reply"));
        entityManager.flush();
        assertEquals(original, jdbc.queryForMap("select *, before_state->>'status' as from_status, after_state->>'status' as to_status, after_state->>'replyText' as reply_text from audit_logs where source = 'patient_review_moderation_history' and entity_id = ? and action = 'REPLY'", id));
        assertEquals("First reply", original.get("reply_text")); assertEquals(admin, original.get("actor_account_id"));
        assertEquals(state, original.get("from_status")); assertEquals(state, original.get("to_status"));
        var edited = jdbc.queryForMap("select *, before_state->>'status' as from_status, after_state->>'status' as to_status, after_state->>'replyText' as reply_text from audit_logs where source = 'patient_review_moderation_history' and entity_id = ? and action = 'EDIT_REPLY'", id);
        assertEquals("Updated reply", edited.get("reply_text")); assertEquals(editingAdmin, edited.get("actor_account_id"));
        assertEquals(state, edited.get("from_status")); assertEquals(state, edited.get("to_status"));
        assertEquals(2, jdbc.queryForObject("select count(*) from audit_logs where source = 'patient_review_moderation_history' and entity_id = ? and action in ('REPLY', 'EDIT_REPLY')", Integer.class, id));
        assertEquals("Updated reply", jdbc.queryForObject("select reply_text from patient_reviews where id = ?", String.class, id));
        assertEquals(editingAdmin, jdbc.queryForObject("select replied_by_account_id from patient_reviews where id = ?", UUID.class, id));
        var currentTime = jdbc.queryForObject("select replied_at from patient_reviews where id = ?", java.sql.Timestamp.class, id);
        assertEquals(firstReplyTime, original.get("occurred_at")); assertEquals(currentTime, edited.get("occurred_at"));
        assertFalse(currentTime.before(firstReplyTime));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor adminActor(UUID accountId) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount(accountId,
                        fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.ADMIN, UUID.randomUUID()), null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    @Test void publicHttpReturnsOnlyPublishedReviewIds() throws Exception {
        var pending = publicReviewFixture(1, "PENDING", Instant.parse("2026-11-01T10:00:00Z"));
        var published = publicReviewFixture(5, "PUBLISHED", Instant.parse("2026-11-01T11:00:00Z"));
        var hidden = publicReviewFixture(1, "HIDDEN", Instant.parse("2026-11-01T12:00:00Z"));
        var page = publicPage(0, 20);
        assertEquals(List.of(published), page.items().stream().map(PublicReview::id).toList());
        assertTrue(page.items().stream().noneMatch(item -> item.id().equals(pending) || item.id().equals(hidden)));
        assertEquals(1, page.totalItems());
        assertEquals(new Summary(5.0, 1), page.summary());
    }
    @Test void hiddenPublishedReviewDisappearsFromPublicHttpList() throws Exception {
        var id = publicReviewFixture(5, "PUBLISHED", Instant.parse("2026-11-01T12:00:00Z"));
        var before = publicPage(0, 20);
        assertEquals(List.of(id), before.items().stream().map(PublicReview::id).toList());
        reviews.decide(id, admin, "HIDE");
        var after = publicPage(0, 20);
        assertTrue(after.items().stream().noneMatch(item -> item.id().equals(id)));
        assertEquals(before.summary().count() - 1, after.summary().count());
        assertEquals(0, after.totalItems());
    }
    @ParameterizedTest @CsvSource({"2,4.5", "3,4.3"})
    void publicHttpSummaryUsesOnlyPublishedRatings(int publishedCount, double expectedAverage) throws Exception {
        var timestamp = Instant.parse("2026-11-01T12:00:00Z");
        for (int i = 0; i < publishedCount; i++) publicReviewFixture(i == 0 ? 5 : 4, "PUBLISHED", timestamp.plusSeconds(i));
        publicReviewFixture(1, "PENDING", timestamp);
        publicReviewFixture(1, "HIDDEN", timestamp);
        assertEquals(new Summary(expectedAverage, publishedCount), publicPage(0, 20).summary());
    }
    @Test void publicHomepageSizeThreeKeepsGlobalSummaryAndNewestOrderAcrossPages() throws Exception {
        var ids = new ArrayList<UUID>();
        var timestamp = Instant.parse("2026-11-01T12:00:00Z");
        for (int i = 0; i < 5; i++) ids.add(publicReviewFixture(5, "PUBLISHED", timestamp.plusSeconds(i)));
        var first = publicPage(0, 3);
        assertEquals(List.of(ids.get(4), ids.get(3), ids.get(2)), first.items().stream().map(PublicReview::id).toList());
        assertEquals(0, first.page()); assertEquals(3, first.size());
        assertEquals(5, first.totalItems()); assertEquals(2, first.totalPages());
        assertEquals(new Summary(5.0, 5), first.summary());
        assertEquals(timestamp.plusSeconds(4), first.items().getFirst().createdAt().toInstant());
        var second = publicPage(1, 3);
        assertEquals(List.of(ids.get(1), ids.get(0)), second.items().stream().map(PublicReview::id).toList());
        assertEquals(1, second.page()); assertEquals(3, second.size());
        assertEquals(5, second.totalItems()); assertEquals(2, second.totalPages());
        assertEquals(first.summary(), second.summary());
        assertEquals(first.summary(), publicPage(0, 1).summary());
    }
    @Test void emptyPublicHttpSummaryPreservesMinimumOnePageConvention() throws Exception {
        var page = publicPage(0, 20);
        assertTrue(page.items().isEmpty()); assertEquals(0, page.totalItems());
        assertEquals(0, page.page()); assertEquals(20, page.size()); assertEquals(1, page.totalPages());
        assertEquals(new Summary(0.0, 0), page.summary());
    }
    private PublicReviewPage publicPage(int page, int size) throws Exception {
        entityManager.flush();
        var response = mvc.perform(get("/api/v1/public/reviews").param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        // Check the wire representation before deserialization normalizes OffsetDateTime to the reader's timezone.
        for (var item : mapper.readTree(response).get("items")) {
            assertEquals(Set.of("id", "rating", "text", "createdAt", "clinicReply"), item.propertyNames());
            assertTrue(item.get("createdAt").asText().endsWith("+07:00"));
        }
        return mapper.readValue(response, PublicReviewPage.class);
    }
    private UUID publicReviewFixture(int rating, String state, Instant submittedAt) {
        UUID visitId = UUID.randomUUID(); visit(visitId, profile, "CLOSED");
        var created = reviews.submit(account, new SubmitReviewRequest(visitId, rating, "Public dataset review"));
        if (state.equals("PUBLISHED")) reviews.decide(created.id(), admin, "APPROVE");
        else if (state.equals("HIDDEN")) reviews.decide(created.id(), admin, "HIDE");
        entityManager.flush();
        jdbc.update("update patient_reviews set submitted_at = ? where id = ?", java.sql.Timestamp.from(submittedAt), created.id());
        entityManager.clear();
        return created.id();
    }
    @Test void ownEligibilitySubmissionModerationReplyPublicAndFilters() {
        assertEquals(List.of(visit), reviews.eligible(account).items().stream().map(EligibleVisit::visitId).toList());
        assertEquals(403, assertThrows(fu.fa26.sep490.g58.otcms_be.review.exception.ReviewOperationException.class,
                () -> reviews.submit(account, new SubmitReviewRequest(otherVisit, 4, "A valid review"))).status());
        // Exceptions are tested in unit tests too; use direct reads below in this transaction.
    }
    @Test void lifecycleAndGlobalCountsWithDatabaseFiltering() {
        long baseline = reviews.published(0, 20, null).summary().count();
        var created = reviews.submit(account, new SubmitReviewRequest(visit, 5, "  A valid review  "));
        UUID id = created.id();
        assertEquals("PENDING", created.status().name());
        entityManager.flush();
        assertEquals("SUBMIT", jdbc.queryForObject("select action from audit_logs where source = 'patient_review_moderation_history' and entity_id = ?", String.class, id));
        assertEquals(baseline, reviews.published(0, 20, null).summary().count());
        reviews.reply(id, admin, new ReplyRequest(" First "));
        reviews.reply(id, admin, new ReplyRequest("Second"));
        reviews.decide(id, admin, "APPROVE");
        assertEquals(baseline + 1, reviews.published(0, 20, null).summary().count());
        var today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var filtered = reviews.list("PUBLISHED", 5, true, today, today, 0, 100, "submittedAt,desc");
        assertTrue(filtered.items().stream().anyMatch(r -> r.id().equals(id)));
        assertTrue(reviews.list("PUBLISHED", 1, true, today, today, 0, 20, null).items().stream().noneMatch(r -> r.id().equals(id)));
        var counts = filtered.counts();
        assertEquals(counts, reviews.list("HIDDEN", 1, false, null, null, 0, 1, null).counts());
        reviews.decide(id, admin, "HIDE");
        assertEquals(baseline, reviews.published(0, 20, null).summary().count());
        reviews.decide(id, admin, "UNHIDE");
        entityManager.flush();
        assertEquals(6, jdbc.queryForObject("select count(*) from audit_logs where source = 'patient_review_moderation_history' and entity_id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'patient_review_moderation_history' and entity_id = ? and action = 'EDIT_REPLY'", Integer.class, id));
        assertEquals(List.of("SUBMIT", "REPLY", "EDIT_REPLY", "APPROVE", "HIDE", "UNHIDE"),
                jdbc.queryForList("select action from audit_logs where source = 'patient_review_moderation_history' and entity_id = ? order by occurred_at", String.class, id));
        assertEquals("Second", reviews.detail(id).reply().text());
    }
    @Test void submittingSameVisitTwiceLeavesOneReviewAndOneSubmitHistory() {
        var created = reviews.submit(account, new SubmitReviewRequest(visit, 5, "First valid review"));
        entityManager.flush();
        assertEquals("PENDING", created.status().name());
        assertEquals(1, jdbc.queryForObject("select count(*) from patient_reviews where visit_id = ?", Integer.class, visit));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where source = 'patient_review_moderation_history' and entity_id = ?", Integer.class, created.id()));

        var failure = assertThrows(fu.fa26.sep490.g58.otcms_be.review.exception.ReviewOperationException.class,
                () -> reviews.submit(account, new SubmitReviewRequest(visit, 1, "Second valid review")));
        assertEquals(409, failure.status());
        assertEquals("MSG-56", failure.code());
        entityManager.flush();
        assertEquals(1, jdbc.queryForObject("select count(*) from patient_reviews where visit_id = ?", Integer.class, visit));
        assertEquals("First valid review", jdbc.queryForObject("select content from patient_reviews where visit_id = ?", String.class, visit));
        assertEquals("PENDING", jdbc.queryForObject("select status from patient_reviews where visit_id = ?", String.class, visit));
        assertEquals(List.of("SUBMIT"), jdbc.queryForList(
                "select h.action from audit_logs h join patient_reviews r on r.id = h.entity_id where h.source = 'patient_review_moderation_history' and r.visit_id = ?",
                String.class, visit));
    }
    @Test void uniqueVisitConstraintRejectsCompetingInsert() throws Exception {
        var created = reviews.submit(account, new SubmitReviewRequest(visit, 4, "A valid review"));
        entityManager.flush();
        var failure = assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into patient_reviews(id, visit_id, patient_profile_id, author_account_id, rating, content, status)
                select ?, visit_id, patient_profile_id, author_account_id, rating, content, status
                from patient_reviews where id = ?
                """, UUID.randomUUID(), created.id()));
        assertHttpConflict(failure);
    }
    @Test void staleEntityVersionRejectedByDatabaseUpdate() throws Exception {
        var created = reviews.submit(account, new SubmitReviewRequest(visit, 4, "A valid review"));
        entityManager.flush(); entityManager.clear();
        var stale = entityManager.find(PatientReviewJpaEntity.class, created.id());
        entityManager.detach(stale);
        reviews.reply(created.id(), admin, new ReplyRequest("First reply"));
        entityManager.flush(); entityManager.clear();
        stale.changeStatus(fu.fa26.sep490.g58.otcms_be.review.model.ReviewStatus.PUBLISHED, Instant.now());
        var failure = assertThrows(jakarta.persistence.OptimisticLockException.class,
                () -> { entityManager.merge(stale); entityManager.flush(); });
        assertHttpConflict(failure);
    }
    @Test void historyCannotBeUpdated() {
        var created = reviews.submit(account, new SubmitReviewRequest(visit, 4, "A valid review"));
        entityManager.flush();
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update audit_logs set action = 'HIDE' where source = 'patient_review_moderation_history' and entity_id = ?", created.id()));
    }
    @Test void historyCannotBeDeleted() {
        var created = reviews.submit(account, new SubmitReviewRequest(visit, 4, "A valid review"));
        entityManager.flush();
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("delete from audit_logs where source = 'patient_review_moderation_history' and entity_id = ?", created.id()));
    }
    @ParameterizedTest @CsvSource({"5,false", "10,true", "1000,true", "1001,false"})
    void reviewCodePointBoundariesMatchPostgres(int count, boolean valid) {
        String text = new String(Character.toChars(0x1F600)).repeat(count);
        if (valid) {
            var created = reviews.submit(account, new SubmitReviewRequest(visit, 5, "  " + text + "  "));
            assertEquals(text, created.text());
            assertEquals(count, jdbc.queryForObject("select char_length(content) from patient_reviews where id=?", Integer.class, created.id()));
        } else {
            var error = assertThrows(fu.fa26.sep490.g58.otcms_be.review.exception.ReviewOperationException.class,
                    () -> reviews.submit(account, new SubmitReviewRequest(visit, 5, "  " + text + "  ")));
            assertEquals(400, error.status()); assertEquals("MSG-57", error.code());
            assertEquals(List.of(new FieldError("text", "MSG-57")), error.fieldErrors());
            assertEquals(0, jdbc.queryForObject("select count(*) from patient_reviews where visit_id=?", Integer.class, visit));
        }
    }
    @ParameterizedTest @CsvSource({"0,false", "1,true", "1000,true", "1001,false"})
    void replyCodePointBoundariesMatchPostgres(int count, boolean valid) {
        var created = reviews.submit(account, new SubmitReviewRequest(visit, 5, "Valid review text"));
        String text = new String(Character.toChars(0x1F600)).repeat(count);
        if (valid) {
            var changed = reviews.reply(created.id(), admin, new ReplyRequest("  " + text + "  "));
            assertEquals(text, changed.reply().text());
            assertEquals(count, jdbc.queryForObject("select char_length(reply_text) from patient_reviews where id=?", Integer.class, created.id()));
        } else {
            var error = assertThrows(fu.fa26.sep490.g58.otcms_be.review.exception.ReviewOperationException.class,
                    () -> reviews.reply(created.id(), admin, new ReplyRequest("  " + text + "  ")));
            assertEquals(400, error.status()); assertEquals("MSG-57", error.code());
            assertEquals(List.of(new FieldError("text", "MSG-57")), error.fieldErrors());
            assertNull(jdbc.queryForObject("select reply_text from patient_reviews where id=?", String.class, created.id()));
        }
    }
    @Test void migrationTablesForeignKeysAndIndexesExist() {
        assertEquals(1, jdbc.queryForObject("select count(*) from flyway_schema_history where version in ('044','44') and success", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = current_schema() "
                + "and table_name in ('patient_reviews')", Integer.class));
        assertEquals(4, jdbc.queryForObject("select count(*) from pg_constraint where contype = 'f' "
                + "and conrelid in ('patient_reviews'::regclass)", Integer.class));
        assertEquals(5, jdbc.queryForObject("select count(*) from pg_indexes where schemaname = current_schema() and indexname in "
                + "('uk_patient_reviews_visit','ix_patient_reviews_patient_submitted','ix_patient_reviews_status_submitted',"
                + "'ix_patient_reviews_author','ix_patient_reviews_replier','ix_patient_review_history_review_time','ix_patient_review_history_actor')", Integer.class));
    }
    @Test void foreignVisitReferenceIsRejected() {
        var error = assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into patient_reviews(id, visit_id, patient_profile_id, author_account_id, rating, content, status)
                values (?, ?, ?, ?, 5, 'A valid review', 'PENDING')
                """, UUID.randomUUID(), UUID.randomUUID(), profile, account));
        assertEquals("23503", sqlState(error));
    }
    @Test void versionIncreasesForReplyAndModeration() {
        var created = reviews.submit(account, new SubmitReviewRequest(visit, 4, "A valid review"));
        long initial = version(created.id());
        reviews.reply(created.id(), admin, new ReplyRequest("A reply")); entityManager.flush();
        assertEquals(initial + 1, version(created.id()));
        reviews.decide(created.id(), admin, "APPROVE"); entityManager.flush();
        assertEquals(initial + 2, version(created.id()));
    }
    @Test void rollbackReviewMutationAlsoRollsBackHistory() {
        endFixtureTransaction();
        var id = new java.util.concurrent.atomic.AtomicReference<UUID>();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThrows(RollbackProbe.class, () -> transaction.execute(status -> {
            fixtures();
            var created = reviews.submit(account, new SubmitReviewRequest(visit, 4, "A valid review")); id.set(created.id());
            reviews.decide(created.id(), admin, "APPROVE"); entityManager.flush();
            assertEquals(2, jdbc.queryForObject("select count(*) from audit_logs where source = 'patient_review_moderation_history' and entity_id = ?", Integer.class, id.get()));
            throw new RollbackProbe();
        }));
        assertAbsentAfterRollback(id.get());
    }
    @Test void laterFailureRollsBackReviewMutationAndHistory() {
        endFixtureTransaction();
        var id = new java.util.concurrent.atomic.AtomicReference<UUID>();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var failure = assertThrows(RuntimeException.class, () -> transaction.execute(status -> {
            fixtures();
            var created = reviews.submit(account, new SubmitReviewRequest(visit, 4, "A valid review")); id.set(created.id());
            reviews.decide(created.id(), admin, "APPROVE");
            entityManager.flush();
            // A second review for the same visit breaks uk_patient_reviews_visit after the history row was written.
            jdbc.update("""
                    insert into patient_reviews(id, visit_id, patient_profile_id, author_account_id, rating, content, status)
                    values (?, ?, ?, ?, 5, 'A duplicate review', 'PENDING')
                    """, UUID.randomUUID(), visit, profile, account);
            return null;
        }));
        assertEquals("23505", sqlState(failure));
        assertAbsentAfterRollback(id.get());
    }
    private long version(UUID id) {
        return jdbc.queryForObject("select version from patient_reviews where id = ?", Long.class, id);
    }
    private void endFixtureTransaction() {
        org.springframework.test.context.transaction.TestTransaction.flagForRollback();
        org.springframework.test.context.transaction.TestTransaction.end();
    }
    private void assertAbsentAfterRollback(UUID id) {
        assertNotNull(id);
        assertEquals(0, jdbc.queryForObject("select count(*) from patient_reviews where id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_logs where source = 'patient_review_moderation_history' and entity_id = ?", Integer.class, id));
    }
    private String sqlState(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof java.sql.SQLException sql) return sql.getSQLState();
        return null;
    }
    private static class RollbackProbe extends RuntimeException { }
    /** Exercise the HTTP advice with the actual exception produced by PostgreSQL/Hibernate above. */
    private void assertHttpConflict(RuntimeException failure) throws Exception {
        var service = org.mockito.Mockito.mock(ReviewService.class);
        org.mockito.Mockito.when(service.decide(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenThrow(failure);
        var actor = new fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount(admin,
                fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.ADMIN, UUID.randomUUID());
        var resolver = new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
            public boolean supportsParameter(org.springframework.core.MethodParameter p) { return p.getParameterType().isInstance(actor); }
            public Object resolveArgument(org.springframework.core.MethodParameter p, org.springframework.web.method.support.ModelAndViewContainer m,
                    org.springframework.web.context.request.NativeWebRequest r, org.springframework.web.bind.support.WebDataBinderFactory b) { return actor; }
        };
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new fu.fa26.sep490.g58.otcms_be.review.controller.AdminReviewController(service))
                .setCustomArgumentResolvers(resolver)
                .setControllerAdvice(new fu.fa26.sep490.g58.otcms_be.review.exception.ReviewApiExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/reviews/" + UUID.randomUUID() + "/approve"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code").value("MSG-56"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").doesNotExist());
    }
    void account(UUID id, String type) {
        jdbc.update("insert into accounts(id, normalized_email, account_type, status, password_hash, created_at, updated_at) "
                + "values (?, ?, ?, 'ACTIVE', 'integration-test', now(), now())", id, "review-" + id + "@example.test", type);
    }
    void profile(UUID id, UUID owner) {
        jdbc.update("insert into patient_profiles(id, account_id, full_name, phone_canonical, phone_owner_type, date_of_birth, gender) "
                + "values (?, ?, 'Review test patient', ?, 'PATIENT', date '1990-01-01', 'OTHER')", id, owner,
                "+849" + String.format("%09d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000L)));
    }
    void visit(UUID id, UUID owner, String status) {
        jdbc.update("insert into visits(id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id, status) "
                + "values (?, ?, 'WALK_IN', now(), now(), ?, ?)", id, owner, account, status);
    }
}
