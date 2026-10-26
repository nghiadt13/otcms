package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.WalkInReceptionReplayJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkInReceptionReplayJpaRepository extends JpaRepository<WalkInReceptionReplayJpaEntity, UUID> {
    Optional<WalkInReceptionReplayJpaEntity> findByReceptionistAccountIdAndIdempotencyKey(UUID receptionistAccountId,
            String idempotencyKey);
}
