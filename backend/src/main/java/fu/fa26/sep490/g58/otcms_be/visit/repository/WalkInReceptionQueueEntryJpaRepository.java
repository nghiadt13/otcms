package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.WalkInReceptionQueueEntryJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalkInReceptionQueueEntryJpaRepository
        extends JpaRepository<WalkInReceptionQueueEntryJpaEntity, UUID> {
    @Query(value = "select set_config('otcms.reception_actor_id', :actorId, true)", nativeQuery = true)
    String setAuditActor(@Param("actorId") String actorId);

    @Query(value = "select set_config('otcms.reception_correlation_id', :correlationId, true)", nativeQuery = true)
    String setAuditCorrelation(@Param("correlationId") String correlationId);
}
