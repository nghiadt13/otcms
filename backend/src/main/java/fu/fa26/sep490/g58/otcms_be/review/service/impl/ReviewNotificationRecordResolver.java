package fu.fa26.sep490.g58.otcms_be.review.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.review.repository.PatientReviewRepository;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationRelatedKind;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationRelatedRecordResolver;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReviewNotificationRecordResolver implements NotificationRelatedRecordResolver {
    private final PatientReviewRepository reviews;
    public ReviewNotificationRecordResolver(PatientReviewRepository reviews) { this.reviews = reviews; }
    public NotificationRelatedKind kind() { return NotificationRelatedKind.REVIEW; }
    public boolean available(AuthenticatedAccount actor, UUID id) {
        return actor.accountRole() == AccountRole.ADMIN && reviews.existsById(id);
    }
}
