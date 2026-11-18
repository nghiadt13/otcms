package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Phase 08 queries over the SERVICE and RESULT_REVIEW rows of {@code queue_entries}. */
public interface ServiceQueueRepository extends JpaRepository<QueueEntryJpaEntity, UUID> {
    /** Statuses that still hold the patient in a room queue. */
    List<String> OPEN = List.of("WAITING", "CALLED", "ABSENT", "IN_EXAMINATION");

    @Query("select q from QueueEntryJpaEntity q where q.stage = 'SERVICE' and q.serviceOrderId = :orderId")
    Optional<QueueEntryJpaEntity> findServiceByOrder(@Param("orderId") UUID orderId);

    @Query("select q from QueueEntryJpaEntity q where q.stage = 'SERVICE' and q.visitId = :visitId and q.status in :open")
    List<QueueEntryJpaEntity> findOpenServiceByVisit(@Param("visitId") UUID visitId, @Param("open") Collection<String> open);

    @Query("select q from QueueEntryJpaEntity q where q.stage = 'SERVICE' and q.serviceOrderId in :orderIds")
    List<QueueEntryJpaEntity> findServiceByOrders(@Param("orderIds") Collection<UUID> orderIds);

    /** Entries of the visit that have a room queue row for the order, whatever their state (to skip re-queueing). */
    @Query("select q.serviceOrderId from QueueEntryJpaEntity q where q.stage = 'SERVICE' and q.visitId = :visitId")
    List<UUID> findQueuedOrderIds(@Param("visitId") UUID visitId);

    /** Most recently finished SERVICE stay of the visit, used to keep the place when the next order is in the same room. */
    @Query("select q from QueueEntryJpaEntity q where q.stage = 'SERVICE' and q.visitId = :visitId and q.finishedAt is not null"
            + " order by q.finishedAt desc, q.id")
    List<QueueEntryJpaEntity> findFinishedServiceByVisit(@Param("visitId") UUID visitId);

    @Query("select count(q) from QueueEntryJpaEntity q where q.stage = :stage and q.roomId = :roomId and q.status in :open")
    long countOpenInRoom(@Param("roomId") UUID roomId, @Param("stage") String stage, @Param("open") Collection<String> open);

    @Query("select coalesce(max(q.queueNumber), 0) from QueueEntryJpaEntity q"
            + " where q.stage = :stage and q.roomId = :roomId and q.clinicDate = :day")
    int maxNumber(@Param("roomId") UUID roomId, @Param("stage") String stage, @Param("day") LocalDate day);

    @Query("select q from QueueEntryJpaEntity q where q.stage = 'EXAM' and q.visitId = :visitId")
    Optional<QueueEntryJpaEntity> findExamByVisit(@Param("visitId") UUID visitId);

    @Query("select q from QueueEntryJpaEntity q where q.stage = 'RESULT_REVIEW' and q.visitId = :visitId and q.status in :open")
    List<QueueEntryJpaEntity> findOpenResultReviewByVisit(@Param("visitId") UUID visitId, @Param("open") Collection<String> open);

    /** Visits that currently sit in the doctor's exam queue (not finished or cancelled). */
    @Query("select distinct q.visitId from QueueEntryJpaEntity q where q.stage = 'EXAM' and q.doctorProfileId = :doctor"
            + " and q.status in ('WAITING', 'CALLED', 'ABSENT', 'IN_EXAMINATION', 'AWAITING_CONCLUSION')")
    List<UUID> findOpenExamVisitIds(@Param("doctor") UUID doctorProfileId);
}
