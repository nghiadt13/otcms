package fu.fa26.sep490.g58.otcms_be.review;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.review.exception.ReviewOperationException;
import fu.fa26.sep490.g58.otcms_be.review.model.ReviewStatus;
import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.*;
import fu.fa26.sep490.g58.otcms_be.review.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.review.repository.*;
import fu.fa26.sep490.g58.otcms_be.review.service.OwnPatientProfileService;
import fu.fa26.sep490.g58.otcms_be.review.service.impl.ReviewServiceImpl;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReviewServiceTest {
    final PatientReviewRepository reviews = mock(PatientReviewRepository.class);
    final PatientReviewHistoryRepository history = mock(PatientReviewHistoryRepository.class);
    final OwnPatientProfileService own = mock(OwnPatientProfileService.class);
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final VisitReviewSourceService visits = mock(VisitReviewSourceService.class);
    final Instant now = Instant.parse("2026-11-01T12:00:00Z");
    final UUID account = UUID.randomUUID(), profileId = UUID.randomUUID(), visitId = UUID.randomUUID();
    final PatientProfileDirectory.ProfileRecord profile = new PatientProfileDirectory.ProfileRecord(
            profileId, account, "Private patient", null, null, null, null);
    final ReviewServiceImpl service = new ReviewServiceImpl(reviews, history, own, profiles, visits,
            Clock.fixed(now, ZoneOffset.UTC), source -> source,
            mock(fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher.class));
    @BeforeEach void setup() {
        when(own.requireOwnProfile(account)).thenReturn(profile);
        when(profiles.findProfile(profileId)).thenReturn(Optional.of(profile));
        source(profileId, "CLOSED");
    }
    void source(UUID owner, String status) {
        when(visits.find(visitId)).thenReturn(Optional.of(new VisitReviewSourceService.Source(
                visitId, owner, status, now, "Doctor", "Examination")));
    }
    PatientReviewJpaEntity review() {
        var review = PatientReviewJpaEntity.submit(visitId, profileId, account, 5, "A valid review", now);
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        return review;
    }
    @Test void eligibleClosedOwnUnreviewedOnly() {
        when(reviews.findByPatientProfileIdOrderBySubmittedAtDescIdDesc(profileId)).thenReturn(List.of());
        when(visits.closedVisits(profileId)).thenReturn(List.of(
                new VisitReviewSourceService.Source(visitId, profileId, "CLOSED", now, "D", "S"),
                new VisitReviewSourceService.Source(UUID.randomUUID(), UUID.randomUUID(), "CLOSED", now, "D", "S"),
                new VisitReviewSourceService.Source(UUID.randomUUID(), profileId, "OPEN", now, "D", "S")));
        var result = service.eligible(account).items();
        assertEquals(1, result.size()); assertEquals(visitId, result.getFirst().visitId());
        assertEquals("SELF", result.getFirst().patient().relationship());
        var existing = review();
        when(reviews.findByPatientProfileIdOrderBySubmittedAtDescIdDesc(profileId)).thenReturn(List.of(existing));
        assertTrue(service.eligible(account).items().isEmpty());
    }
    @Test void submitPendingTrimmedAndHistory() {
        var result = service.submit(account, new SubmitReviewRequest(visitId, 5, "  A valid review  "));
        assertEquals(ReviewStatus.PENDING, result.status()); assertEquals("A valid review", result.text());
        var event = ArgumentCaptor.forClass(PatientReviewHistoryJpaEntity.class);
        verify(history).save(event.capture()); assertEquals("SUBMIT", event.getValue().getAction());
        assertNull(event.getValue().getFromStatus()); assertEquals(ReviewStatus.PENDING, event.getValue().getToStatus());
    }
    @ParameterizedTest @ValueSource(ints = {-1, 0, 6, 100})
    void invalidRating(int rating) {
        var error = assertThrows(ReviewOperationException.class,
                () -> service.submit(account, new SubmitReviewRequest(visitId, rating, "A valid review")));
        assertEquals(400, error.status()); assertEquals("rating", error.fieldErrors().getFirst().field());
        verifyNoInteractions(history);
    }
    @ParameterizedTest @ValueSource(strings = {"", "          ", " short "})
    void invalidTrimmedText(String text) {
        var error = assertThrows(ReviewOperationException.class,
                () -> service.submit(account, new SubmitReviewRequest(visitId, 5, text)));
        assertEquals("MSG-57", error.code()); assertEquals("text", error.fieldErrors().getFirst().field());
    }
    @Test void openDuplicateForeignAndMissingRejected() {
        source(profileId, "OPEN"); expectSubmit(409);
        source(UUID.randomUUID(), "CLOSED"); expectSubmit(403);
        when(visits.find(visitId)).thenReturn(Optional.empty()); expectSubmit(403);
        source(profileId, "CLOSED"); when(reviews.existsByVisitId(visitId)).thenReturn(true); expectSubmit(409);
        verifyNoInteractions(history);
    }
    void expectSubmit(int status) {
        var error = assertThrows(ReviewOperationException.class,
                () -> service.submit(account, new SubmitReviewRequest(visitId, 5, "A valid review")));
        assertEquals(status, error.status()); assertEquals("MSG-56", error.code());
    }
    @Test void transitionsAndActions() {
        var r = review();
        assertEquals(List.of("approve", "hide", "reply"), service.detail(r.getId()).allowedActions());
        assertEquals(ReviewStatus.PUBLISHED, service.decide(r.getId(), account, "APPROVE").status());
        assertEquals(ReviewStatus.HIDDEN, service.decide(r.getId(), account, "HIDE").status());
        assertEquals(List.of("unhide", "reply"), service.detail(r.getId()).allowedActions());
        assertEquals(ReviewStatus.PUBLISHED, service.decide(r.getId(), account, "UNHIDE").status());
        assertEquals(409, assertThrows(ReviewOperationException.class,
                () -> service.decide(r.getId(), account, "APPROVE")).status());
        r.changeStatus(ReviewStatus.PENDING, now);
        assertEquals(ReviewStatus.HIDDEN, service.decide(r.getId(), account, "HIDE").status());
        assertThrows(ReviewOperationException.class, () -> service.decide(r.getId(), account, "HIDE"));
        verify(history, times(4)).save(any());
    }
    @ParameterizedTest
    @CsvSource({"PENDING,APPROVE,PUBLISHED", "PENDING,HIDE,HIDDEN", "PUBLISHED,HIDE,HIDDEN",
            "HIDDEN,UNHIDE,PUBLISHED", "PENDING,UNHIDE,CONFLICT", "PUBLISHED,APPROVE,CONFLICT",
            "PUBLISHED,UNHIDE,CONFLICT", "HIDDEN,APPROVE,CONFLICT", "HIDDEN,HIDE,CONFLICT"})
    void everyTransitionHasExpectedStatusAndHistory(String from, String action, String target) {
        var r = review(); r.changeStatus(ReviewStatus.valueOf(from), now);
        if (target.equals("CONFLICT")) {
            assertEquals(409, assertThrows(ReviewOperationException.class, () -> service.decide(r.getId(), account, action)).status());
            verifyNoInteractions(history);
        } else {
            assertEquals(target, service.decide(r.getId(), account, action).status().name());
            var event = ArgumentCaptor.forClass(PatientReviewHistoryJpaEntity.class);
            verify(history).save(event.capture());
            assertEquals(from, event.getValue().getFromStatus().name());
            assertEquals(target, event.getValue().getToStatus().name()); assertEquals(action, event.getValue().getAction());
        }
    }
    @ParameterizedTest
    @CsvSource({"PENDING,false,approve|hide|reply", "PENDING,true,approve|hide|editReply",
            "PUBLISHED,false,hide|reply", "PUBLISHED,true,hide|editReply",
            "HIDDEN,false,unhide|reply", "HIDDEN,true,unhide|editReply"})
    void allowedActionsExactlyMatchStatusAndReply(String status, boolean replied, String expected) {
        var r = review(); r.changeStatus(ReviewStatus.valueOf(status), now);
        if (replied) r.reply("Existing reply", account, now);
        assertEquals(List.of(expected.split("\\|")), service.detail(r.getId()).allowedActions());
    }
    @Test void missingAndOversizedFieldsRejectedBeforeVisitLookup() {
        for (SubmitReviewRequest body : List.of(new SubmitReviewRequest(visitId, null, "A valid review"),
                new SubmitReviewRequest(visitId, 5, null), new SubmitReviewRequest(visitId, 5, "x".repeat(1001))))
            assertEquals("MSG-57", assertThrows(ReviewOperationException.class, () -> service.submit(account, body)).code());
        verify(visits, never()).find(any());
    }
    @Test void replyOverwriteAndHistory() {
        var r = review();
        assertEquals("First", service.reply(r.getId(), account, new ReplyRequest(" First ")).reply().text());
        assertEquals("Second", service.reply(r.getId(), account, new ReplyRequest("Second")).reply().text());
        assertTrue(service.detail(r.getId()).allowedActions().contains("editReply"));
        var events = ArgumentCaptor.forClass(PatientReviewHistoryJpaEntity.class);
        verify(history, times(2)).save(events.capture());
        assertEquals(List.of("REPLY", "EDIT_REPLY"), events.getAllValues().stream().map(PatientReviewHistoryJpaEntity::getAction).toList());
        for (String text : List.of("  ", "x".repeat(1001)))
            assertEquals(400, assertThrows(ReviewOperationException.class,
                    () -> service.reply(r.getId(), account, new ReplyRequest(text))).status());
    }
    @Test void invalidFiltersAndGlobalCounts() {
        assertEquals("MSG-54", assertThrows(ReviewOperationException.class, () -> service.list(null, null, null,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1), 0, 20, null)).code());
        when(reviews.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(reviews.countByStatus(ReviewStatus.PENDING)).thenReturn(2L);
        when(reviews.countByStatus(ReviewStatus.PUBLISHED)).thenReturn(3L);
        when(reviews.countByStatus(ReviewStatus.HIDDEN)).thenReturn(4L); when(reviews.count()).thenReturn(9L);
        var page = service.list("PENDING", 5, false, null, null, 0, 20, null);
        assertEquals(new Counts(2, 3, 4, 9), page.counts());
    }
    @Test void publicSummaryGlobalAndNoIdentityFields() {
        var r = review(); r.changeStatus(ReviewStatus.PUBLISHED, now);
        when(reviews.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 1), 3));
        when(reviews.averageRating(ReviewStatus.PUBLISHED)).thenReturn(4.25);
        when(reviews.countByStatus(ReviewStatus.PUBLISHED)).thenReturn(3L);
        var result = service.published(0, 1, null);
        assertEquals(new Summary(4.3, 3), result.summary());
        assertEquals(Set.of("id", "rating", "text", "createdAt", "clinicReply"),
                Arrays.stream(PublicReview.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)
                        .collect(java.util.stream.Collectors.toSet()));
    }
}
