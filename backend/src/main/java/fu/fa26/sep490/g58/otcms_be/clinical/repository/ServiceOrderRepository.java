package fu.fa26.sep490.g58.otcms_be.clinical.repository;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.UUID;
public interface ServiceOrderRepository extends JpaRepository<ServiceOrderJpaEntity,UUID>{ List<ServiceOrderJpaEntity> findByOrderingRoundId(UUID roundId); }
