package fu.fa26.sep490.g58.otcms_be.clinical.repository;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.OrderingRoundJpaEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional; import java.util.UUID;
public interface OrderingRoundRepository extends JpaRepository<OrderingRoundJpaEntity,UUID>{ Optional<OrderingRoundJpaEntity> findTopByVisitIdOrderByRoundNumberDesc(UUID visitId); }
