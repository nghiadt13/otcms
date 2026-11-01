package fu.fa26.sep490.g58.otcms_be.review.model.entity;

import fu.fa26.sep490.g58.otcms_be.review.model.ReviewStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_reviews")
public class PatientReviewJpaEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID visitId;
    @Column(nullable = false) private UUID patientProfileId;
    @Column(nullable = false) private UUID authorAccountId;
    @Column(nullable = false) private int rating;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ReviewStatus status;
    @Column(columnDefinition = "text") private String replyText;
    private Instant repliedAt;
    private UUID repliedByAccountId;
    @Column(nullable = false) private Instant submittedAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected PatientReviewJpaEntity() { }

    public static PatientReviewJpaEntity submit(UUID visitId, UUID profileId, UUID authorId,
            int rating, String content, Instant now) {
        var review = new PatientReviewJpaEntity();
        review.id = UUID.randomUUID(); review.visitId = visitId; review.patientProfileId = profileId;
        review.authorAccountId = authorId; review.rating = rating; review.content = content;
        review.status = ReviewStatus.PENDING; review.submittedAt = now; review.updatedAt = now;
        return review;
    }

    public void changeStatus(ReviewStatus target, Instant now) { status = target; updatedAt = now; }
    public void reply(String text, UUID actor, Instant now) {
        replyText = text; repliedAt = now; repliedByAccountId = actor; updatedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getVisitId() { return visitId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID getAuthorAccountId() { return authorAccountId; }
    public int getRating() { return rating; }
    public String getContent() { return content; }
    public ReviewStatus getStatus() { return status; }
    public String getReplyText() { return replyText; }
    public Instant getRepliedAt() { return repliedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Long getVersion() { return version; }
}
