package fu.fa26.sep490.g58.otcms_be.review.repository;

import fu.fa26.sep490.g58.otcms_be.review.model.ReviewStatus;
import fu.fa26.sep490.g58.otcms_be.review.model.entity.PatientReviewJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface PatientReviewRepository extends JpaRepository<PatientReviewJpaEntity, UUID>,
        JpaSpecificationExecutor<PatientReviewJpaEntity> {
    boolean existsByVisitId(UUID visitId);
    List<PatientReviewJpaEntity> findByPatientProfileIdOrderBySubmittedAtDescIdDesc(UUID profileId);
    long countByStatus(ReviewStatus status);
    @Query("select coalesce(avg(r.rating), 0.0) from PatientReviewJpaEntity r where r.status = :status")
    double averageRating(ReviewStatus status);
}
