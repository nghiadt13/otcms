package fu.fa26.sep490.g58.otcms_be.clinical.repository;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.UUID;
public interface ServiceOrderRepository extends JpaRepository<ServiceOrderJpaEntity,UUID>{
    List<ServiceOrderJpaEntity> findByOrderingRoundId(UUID roundId);
    List<ServiceOrderJpaEntity> findByOrderingRoundIdIn(java.util.Collection<UUID> roundIds);
    List<ServiceOrderJpaEntity> findByVisitId(UUID visitId);
    java.util.Optional<ServiceOrderJpaEntity> findFirstByOrderingRoundIdAndRoundHeadTrue(UUID roundId);
    java.util.Optional<ServiceOrderJpaEntity> findByRequestingDoctorIdAndSubmitIdempotencyKeyAndRoundHeadTrue(UUID doctorId, String submitIdempotencyKey);
    java.util.Optional<ServiceOrderJpaEntity> findTopByVisitIdAndRoundHeadTrueOrderByRoundNumberDesc(UUID visitId);
    org.springframework.data.domain.Page<ServiceOrderJpaEntity> findAllByRoundHeadTrue(org.springframework.data.domain.Pageable pageable);
    List<ServiceOrderJpaEntity> findByVisitIdAndRoundHeadTrueOrderByRoundNumberAsc(UUID visitId);
}
