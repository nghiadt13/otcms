package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.WalkInReceptionQueueHistoryJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkInReceptionQueueHistoryJpaRepository
        extends JpaRepository<WalkInReceptionQueueHistoryJpaEntity, UUID> { }
