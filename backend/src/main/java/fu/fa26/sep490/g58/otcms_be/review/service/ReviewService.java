package fu.fa26.sep490.g58.otcms_be.review.service;

import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.*;
import java.time.LocalDate;
import java.util.UUID;

public interface ReviewService {
    Items<EligibleVisit> eligible(UUID accountId);
    Items<MyReview> mine(UUID accountId);
    MyReview submit(UUID accountId, SubmitReviewRequest request);
    PublicReviewPage published(int page, int size, String sort);
    ReviewListPage list(String status, Integer rating, Boolean replied, LocalDate from, LocalDate to,
            int page, int size, String sort);
    ReviewDetail detail(UUID id);
    ReviewDetail decide(UUID id, UUID actorId, String action);
    ReviewDetail reply(UUID id, UUID actorId, ReplyRequest request);
}
