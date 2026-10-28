package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.WalkInReceptionQueueEntryJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalkInReceptionQueueEntryJpaRepository
        extends JpaRepository<WalkInReceptionQueueEntryJpaEntity, UUID> {
    @Query(value = "select set_config('otcms.reception_actor_id', :actorId, true)", nativeQuery = true)
    String setAuditActor(@Param("actorId") String actorId);

    @Query(value = "select set_config('otcms.reception_correlation_id', :correlationId, true)", nativeQuery = true)
    String setAuditCorrelation(@Param("correlationId") String correlationId);

    @Query(value = "select id from walk_in_reception_queue_entries where visit_id = :visitId "
            + "and status = 'WAITING' for update", nativeQuery = true)
    List<UUID> findWaitingEntryIdsForUpdate(@Param("visitId") UUID visitId);

    @Modifying
    @Query(value = "update walk_in_reception_queue_entries set status = 'LEFT_RECEPTION', left_at = :leftAt, "
            + "exit_reason = :reason where id = :entryId and status = 'WAITING'", nativeQuery = true)
    int leaveReception(@Param("entryId") UUID entryId, @Param("leftAt") Instant leftAt,
            @Param("reason") String reason);
}
