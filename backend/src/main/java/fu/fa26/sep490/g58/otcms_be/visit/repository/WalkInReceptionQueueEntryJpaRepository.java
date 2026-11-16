package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reception-queue rows of {@code queue_entries}. Every query filters {@code stage = 'RECEPTION'}: the exam queue
 * shares the table and the WAITING status. The old database trigger that wrote the queue history and checked the
 * actor account is gone; callers record the transition with AuditLogWriter (VisitAuditEntries) themselves.
 */
public interface WalkInReceptionQueueEntryJpaRepository extends JpaRepository<QueueEntryJpaEntity, UUID> {
    @Query(value = "select id from queue_entries where visit_id = :visitId "
            + "and stage = 'RECEPTION' and status = 'WAITING' for update", nativeQuery = true)
    List<UUID> findWaitingReceptionIdsForUpdate(@Param("visitId") UUID visitId);

    @Modifying
    @Query(value = "update queue_entries set status = 'LEFT_RECEPTION', left_at = :leftAt, exit_reason = :reason "
            + "where id = :entryId and stage = 'RECEPTION' and status = 'WAITING'", nativeQuery = true)
    int leaveReception(@Param("entryId") UUID entryId, @Param("leftAt") Instant leftAt,
            @Param("reason") String reason);
}
