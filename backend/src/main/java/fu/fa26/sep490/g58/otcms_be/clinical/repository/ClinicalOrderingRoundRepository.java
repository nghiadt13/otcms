package fu.fa26.sep490.g58.otcms_be.clinical.repository;

import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalOrderingRoundJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Ordering rounds. Pages of all rounds use findAll(Pageable); batches by id use findAllById. */
public interface ClinicalOrderingRoundRepository extends JpaRepository<ClinicalOrderingRoundJpaEntity, UUID> {
    Optional<ClinicalOrderingRoundJpaEntity> findTopByVisitIdOrderByRoundNumberDesc(UUID visitId);
    List<ClinicalOrderingRoundJpaEntity> findByVisitIdOrderByRoundNumberAsc(UUID visitId);
}
