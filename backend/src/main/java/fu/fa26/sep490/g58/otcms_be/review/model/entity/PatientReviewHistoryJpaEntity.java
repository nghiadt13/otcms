package fu.fa26.sep490.g58.otcms_be.review.model.entity;

import fu.fa26.sep490.g58.otcms_be.review.model.ReviewStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity @Immutable
@Table(name = "patient_review_moderation_history")
public class PatientReviewHistoryJpaEntity {
    @Id private UUID id;
    private UUID reviewId;
    @Column(length = 16) private String action;
    @Enumerated(EnumType.STRING) @Column(length = 16) private ReviewStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(length = 16) private ReviewStatus toStatus;
    @Column(columnDefinition = "text") private String replyText;
    private UUID actorAccountId;
    private Instant occurredAt;
    protected PatientReviewHistoryJpaEntity() { }
    public PatientReviewHistoryJpaEntity(PatientReviewJpaEntity review, String action,
            ReviewStatus from, UUID actor, Instant now) {
        id = UUID.randomUUID(); reviewId = review.getId(); this.action = action;
        fromStatus = from; toStatus = review.getStatus();
        replyText = ("REPLY".equals(action) || "EDIT_REPLY".equals(action)) ? review.getReplyText() : null;
        actorAccountId = actor; occurredAt = now;
    }
    public String getAction() { return action; }
    public ReviewStatus getFromStatus() { return fromStatus; }
    public ReviewStatus getToStatus() { return toStatus; }
}
