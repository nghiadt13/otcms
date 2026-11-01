package fu.fa26.sep490.g58.otcms_be.review.repository;

import fu.fa26.sep490.g58.otcms_be.review.model.entity.PatientReviewHistoryJpaEntity;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Append-only API: deliberately exposes no update or delete operation. */
public interface PatientReviewHistoryRepository extends Repository<PatientReviewHistoryJpaEntity, UUID> {
    PatientReviewHistoryJpaEntity save(PatientReviewHistoryJpaEntity event);
}
