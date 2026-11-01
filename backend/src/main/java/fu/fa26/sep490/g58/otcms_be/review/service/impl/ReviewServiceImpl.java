package fu.fa26.sep490.g58.otcms_be.review.service.impl;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationRelatedKind;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessage;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.review.exception.ReviewOperationException;
import fu.fa26.sep490.g58.otcms_be.review.model.ReviewStatus;
import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.*;
import fu.fa26.sep490.g58.otcms_be.review.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.review.repository.*;
import fu.fa26.sep490.g58.otcms_be.review.service.*;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final PatientReviewRepository reviews;
    private final PatientReviewHistoryRepository history;
    private final OwnPatientProfileService ownProfiles;
    private final PatientProfileDirectory profiles;
    private final VisitReviewSourceService visits;
    private final Clock clock;
    private final ReviewVisitContextService visitContext;
    private final NotificationPublisher notifications;
    public ReviewServiceImpl(PatientReviewRepository reviews, PatientReviewHistoryRepository history,
            OwnPatientProfileService ownProfiles, PatientProfileDirectory profiles,
            VisitReviewSourceService visits, Clock clock, ReviewVisitContextService visitContext,
            NotificationPublisher notifications) {
        this.reviews = reviews; this.history = history; this.ownProfiles = ownProfiles;
        this.profiles = profiles; this.visits = visits; this.clock = clock; this.visitContext = visitContext;
        this.notifications = notifications;
    }
    public Items<EligibleVisit> eligible(UUID accountId) {
        var profile = ownProfiles.requireOwnProfile(accountId);
        var reviewed = new HashSet<UUID>();
        reviews.findByPatientProfileIdOrderBySubmittedAtDescIdDesc(profile.id()).forEach(r -> reviewed.add(r.getVisitId()));
        return new Items<>(visits.closedVisits(profile.id()).stream()
                .filter(v -> profile.id().equals(v.patientProfileId()) && "CLOSED".equals(v.status()) && !reviewed.contains(v.id()))
                .map(visitContext::enrich)
                .map(v -> new EligibleVisit(v.id(), date(v.arrivedAt()), v.serviceName(), patient(profile))).toList());
    }
    public Items<MyReview> mine(UUID accountId) {
        var profile = ownProfiles.requireOwnProfile(accountId);
        return new Items<>(reviews.findByPatientProfileIdOrderBySubmittedAtDescIdDesc(profile.id()).stream()
                .map(r -> mine(r, profile)).toList());
    }
    @Transactional
    public MyReview submit(UUID accountId, SubmitReviewRequest request) {
        var errors = new ArrayList<FieldError>();
        if (request == null || request.rating() == null || request.rating() < 1 || request.rating() > 5)
            errors.add(new FieldError("rating", "MSG-57"));
        if (request == null || !validText(request.text(), 10)) errors.add(new FieldError("text", "MSG-57"));
        if (request == null || request.visitId() == null) errors.add(new FieldError("visitId", "MSG-57"));
        if (!errors.isEmpty()) throw new ReviewOperationException(400, "MSG-57", errors);
        var profile = ownProfiles.requireOwnProfile(accountId);
        var visit = visits.find(request.visitId()).filter(v -> profile.id().equals(v.patientProfileId()))
                .orElseThrow(() -> new ReviewOperationException(403, "MSG-56"));
        if (!"CLOSED".equals(visit.status()) || reviews.existsByVisitId(visit.id())) conflict();
        var now = clock.instant();
        var review = PatientReviewJpaEntity.submit(visit.id(), profile.id(), accountId,
                request.rating(), request.text().trim(), now);
        reviews.saveAndFlush(review);
        history.save(new PatientReviewHistoryJpaEntity(review, "SUBMIT", null, accountId, now));
        notifications.publishToRole(fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.ADMIN,
                new NotificationMessage("REVIEW_SUBMITTED", "Đánh giá mới cần duyệt",
                        "Một đánh giá mới đang chờ kiểm duyệt.",
                        NotificationRelatedKind.REVIEW, review.getId()));
        return mine(review, profile);
    }
    public PublicReviewPage published(int page, int size, String sort) {
        var spec = (Specification<PatientReviewJpaEntity>) (r, q, b) -> b.equal(r.get("status"), ReviewStatus.PUBLISHED);
        var result = page(spec, page, size, sort, "submittedAt,desc");
        var items = result.getContent().stream().map(r -> new PublicReview(r.getId(), r.getRating(),
                r.getContent(), time(r.getSubmittedAt()), r.getReplyText())).toList();
        var summary = new Summary(Math.round(reviews.averageRating(ReviewStatus.PUBLISHED) * 10.0) / 10.0,
                reviews.countByStatus(ReviewStatus.PUBLISHED));
        return new PublicReviewPage(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                Math.max(1, result.getTotalPages()), summary);
    }
    public ReviewListPage list(String status, Integer rating, Boolean replied, LocalDate from, LocalDate to,
            int page, int size, String sort) {
        ReviewStatus state = null;
        try { if (status != null && !status.isBlank()) state = ReviewStatus.valueOf(status); }
        catch (IllegalArgumentException invalid) { throw new ReviewOperationException(400, "MSG-54"); }
        if ((rating != null && (rating < 1 || rating > 5)) || (from != null && to != null && from.isAfter(to))
                || LocalDate.MAX.equals(to)) throw new ReviewOperationException(400, "MSG-54");
        var filterState = state;
        Specification<PatientReviewJpaEntity> spec = (r, q, b) -> {
            var filters = new ArrayList<Predicate>();
            if (filterState != null) filters.add(b.equal(r.get("status"), filterState));
            if (rating != null) filters.add(b.equal(r.get("rating"), rating));
            if (replied != null) filters.add(replied ? b.isNotNull(r.get("replyText")) : b.isNull(r.get("replyText")));
            if (from != null) filters.add(b.greaterThanOrEqualTo(r.get("submittedAt"), from.atStartOfDay(ZONE).toInstant()));
            if (to != null) filters.add(b.lessThan(r.get("submittedAt"), to.plusDays(1).atStartOfDay(ZONE).toInstant()));
            return b.and(filters.toArray(Predicate[]::new));
        };
        var result = page(spec, page, size, sort, "submittedAt,desc");
        var counts = new Counts(reviews.countByStatus(ReviewStatus.PENDING), reviews.countByStatus(ReviewStatus.PUBLISHED),
                reviews.countByStatus(ReviewStatus.HIDDEN), reviews.count());
        return new ReviewListPage(result.getContent().stream().map(this::item).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), Math.max(1, result.getTotalPages()), counts);
    }
    public ReviewDetail detail(UUID id) { return detail(require(id)); }
    @Transactional
    public ReviewDetail decide(UUID id, UUID actorId, String action) {
        var review = require(id);
        var from = review.getStatus();
        ReviewStatus target;
        switch (action) {
            case "APPROVE" -> { if (from != ReviewStatus.PENDING) conflict(); target = ReviewStatus.PUBLISHED; }
            case "HIDE" -> { if (from == ReviewStatus.HIDDEN) conflict(); target = ReviewStatus.HIDDEN; }
            case "UNHIDE" -> { if (from != ReviewStatus.HIDDEN) conflict(); target = ReviewStatus.PUBLISHED; }
            default -> throw new ReviewOperationException(409, "MSG-56");
        }
        var now = clock.instant();
        review.changeStatus(target, now);
        reviews.saveAndFlush(review);
        history.save(new PatientReviewHistoryJpaEntity(review, action, from, actorId, now));
        return detail(review);
    }
    @Transactional
    public ReviewDetail reply(UUID id, UUID actorId, ReplyRequest request) {
        if (request == null || !validText(request.text(), 1)) throw new ReviewOperationException(400, "MSG-57",
                List.of(new FieldError("text", "MSG-57")));
        var review = require(id);
        var action = review.getReplyText() == null ? "REPLY" : "EDIT_REPLY";
        var now = clock.instant();
        review.reply(request.text().trim(), actorId, now);
        reviews.saveAndFlush(review);
        history.save(new PatientReviewHistoryJpaEntity(review, action, review.getStatus(), actorId, now));
        return detail(review);
    }
    private Page<PatientReviewJpaEntity> page(Specification<PatientReviewJpaEntity> spec, int page, int size,
            String sort, String defaultSort) {
        var parts = (sort == null || sort.isBlank() ? defaultSort : sort).split(",", -1);
        if (parts.length != 2 || !Set.of("submittedAt", "rating").contains(parts[0])
                || !Set.of("asc", "desc").contains(parts[1])) throw new ReviewOperationException(400, "MSG-54");
        int normalizedSize = Math.min(100, Math.max(1, size));
        var ordering = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]).and(Sort.by(Sort.Direction.DESC, "id"));
        var result = reviews.findAll(spec, PageRequest.of(Math.max(0, page), normalizedSize, ordering));
        if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages())
            return reviews.findAll(spec, PageRequest.of(result.getTotalPages() - 1, normalizedSize, ordering));
        if (result.getTotalElements() == 0 && result.getNumber() != 0)
            return new PageImpl<>(List.of(), PageRequest.of(0, normalizedSize, ordering), 0);
        return result;
    }
    private PatientReviewJpaEntity require(UUID id) {
        return reviews.findById(id).orElseThrow(() -> new ReviewOperationException(404, "MSG-50"));
    }
    private MyReview mine(PatientReviewJpaEntity review, PatientProfileDirectory.ProfileRecord profile) {
        var source = source(review);
        return new MyReview(review.getId(), review.getVisitId(), date(source.arrivedAt()), review.getRating(),
                review.getContent(), review.getStatus(), review.getReplyText(), time(review.getSubmittedAt()), patient(profile));
    }
    private ReviewListItem item(PatientReviewJpaEntity r) {
        var source = source(r);
        String name = profiles.findProfile(r.getPatientProfileId()).map(PatientProfileDirectory.ProfileRecord::fullName).orElse("");
        return new ReviewListItem(r.getId(), time(r.getSubmittedAt()), name, r.getRating(), r.getContent(), r.getStatus(),
                r.getReplyText() != null, new Visit(date(source.arrivedAt()), source.doctorName(), source.serviceName()), actions(r));
    }
    private ReviewDetail detail(PatientReviewJpaEntity r) {
        var item = item(r);
        return new ReviewDetail(item.id(), item.submittedAt(), item.patientName(), item.rating(), item.content(),
                item.status(), item.replied(), item.visit(), item.allowedActions(), r.getReplyText() == null ? null
                        : new Reply(r.getReplyText(), time(r.getRepliedAt())));
    }
    private VisitReviewSourceService.Source source(PatientReviewJpaEntity r) {
        return visitContext.enrich(visits.find(r.getVisitId()).orElseThrow(() -> new ReviewOperationException(404, "MSG-50")));
    }
    public static List<String> actions(PatientReviewJpaEntity r) {
        String reply = r.getReplyText() == null ? "reply" : "editReply";
        return switch (r.getStatus()) {
            case PENDING -> List.of("approve", "hide", reply);
            case PUBLISHED -> List.of("hide", reply);
            case HIDDEN -> List.of("unhide", reply);
        };
    }
    private static boolean validText(String text, int minimum) {
        if (text == null) return false;
        String value = text.trim();
        int length = value.codePointCount(0, value.length());
        return length >= minimum && length <= 1000;
    }
    private static void conflict() { throw new ReviewOperationException(409, "MSG-56"); }
    private static Patient patient(PatientProfileDirectory.ProfileRecord profile) { return new Patient(profile.fullName(), "SELF"); }
    private static LocalDate date(Instant instant) { return instant.atZone(ZONE).toLocalDate(); }
    private static OffsetDateTime time(Instant instant) { return instant.atZone(ZONE).toOffsetDateTime(); }
}
