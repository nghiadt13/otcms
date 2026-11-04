package fu.fa26.sep490.g58.otcms_be.clinical.repository;

import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalImagingRequestReplayJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalImagingRequestReplayRepository extends JpaRepository<ClinicalImagingRequestReplayJpaEntity, UUID> {
    Optional<ClinicalImagingRequestReplayJpaEntity> findByDoctorProfileIdAndIdempotencyKey(UUID doctorProfileId, String key);
}
